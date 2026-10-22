package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.ProductionTaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskExecution;
import dev.twelvetimes.vector.task.TaskFailure;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskTerminalService;
import dev.twelvetimes.vector.task.TaskWriteBarrier;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

import static dev.twelvetimes.vector.task.TaskTerminalService.TerminalResult.ALREADY_APPLIED;
import static dev.twelvetimes.vector.task.TaskTerminalService.TerminalResult.APPLIED;
import static dev.twelvetimes.vector.task.TaskTerminalService.TerminalResult.STALE;
import static dev.twelvetimes.vector.task.TaskTerminalService.TerminalResult.WRITE_CLOSED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskTerminalServiceTest {
    @TempDir Path directory;

    @Test
    void 真实B1正常及空文均成功且同令牌重放幂等() {
        try (var context = B2TestSupport.start(directory)) {
            for (String text : new String[] {"", "正常中文🙂"}) {
                TaskExecution execution = claim(context, "d" + text.length(), text);
                var service = service(context, context.getBean(TaskTerminalMapper.class), context.getBean(PlatformTransactionManager.class));
                float[] vector = context.getBean(TextEmbedder.class).embed(text);
                assertEquals(APPLIED, service.succeed(execution, vector));
                var first = context.getBean(TaskTerminalMapper.class).snapshot(execution.taskId());
                assertEquals(ALREADY_APPLIED, service.succeed(execution, vector));
                var snapshot = context.getBean(TaskTerminalMapper.class).snapshot(execution.taskId());
                assertArrayEquals(VectorCodec.encode(vector), snapshot.vectorBytes());
                assertEquals(snapshot.finishedAt(), snapshot.vectorCompletedAt());
                assertEquals(first.finishedAt(), snapshot.finishedAt());
                assertTrue(context.getBean(DocumentQueryService.class).detail(execution.documentId()).vectorReady());
            }
        }
    }

    @Test
    void 两表第二步失败整体回滚并降级而非伪FAILED() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "rollback", "真实文本");
            var mapper = decorate(context.getBean(TaskTerminalMapper.class), name -> {
                if (name.equals("writeVector")) { throw new DataAccessResourceFailureException("仅测试可见故障"); }
            });
            var service = service(context, mapper, context.getBean(PlatformTransactionManager.class));
            assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                    () -> service.succeed(execution, new float[256])).code());
            var snapshot = context.getBean(TaskTerminalMapper.class).snapshot(execution.taskId());
            assertEquals("RUNNING", snapshot.status());
            assertNull(snapshot.finishedAt());
            assertNull(snapshot.vectorBytes());
            assertFalse(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 旧令牌迟到成功与失败均不能覆盖新尝试() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution old = claim(context, "stale", "原文");
            String current = UUID.randomUUID().toString();
            new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE embedding_tasks SET run_token=?,attempt_count=2 WHERE task_id=?", current, old.taskId());
            var service = service(context, context.getBean(TaskTerminalMapper.class), context.getBean(PlatformTransactionManager.class));
            assertEquals(STALE, service.succeed(old, new float[256]));
            assertEquals(STALE, service.fail(old, TaskFailure.COMPUTATION_FAILED));
            var snapshot = context.getBean(TaskTerminalMapper.class).snapshot(old.taskId());
            assertEquals("RUNNING", snapshot.status());
            assertEquals(current, snapshot.runToken());
            assertNull(snapshot.vectorBytes());
            assertTrue(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 计算期间失效后终态不覆盖失效或计数() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "invalidated", "原文");
            var invalidation = context.getBean(DocumentQueryService.class).invalidate("invalidated");
            new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE documents SET hit_count=9007199254740993 WHERE document_id='invalidated'");
            var service = service(context, context.getBean(TaskTerminalMapper.class), context.getBean(PlatformTransactionManager.class));
            assertEquals(APPLIED, service.succeed(execution, context.getBean(TextEmbedder.class).embed("原文")));
            var detail = context.getBean(DocumentQueryService.class).detail("invalidated");
            assertTrue(detail.invalidated());
            assertTrue(detail.vectorReady());
            assertEquals(invalidation.invalidatedAt(), detail.invalidatedAt());
            assertEquals("9007199254740993", detail.hitCount());
        }
    }

    @Test
    void 计算输入及版本失败分类持久化且错误脱敏() {
        try (var context = B2TestSupport.start(directory)) {
            var service = service(context, context.getBean(TaskTerminalMapper.class), context.getBean(PlatformTransactionManager.class));
            TextEmbedder actual = context.getBean(TextEmbedder.class);
            TaskExecution calculation = claim(context, "calculation", "原文");
            TextEmbedder throwing = new TextEmbedder() {
                @Override public String version() { return actual.version(); }
                @Override public float[] embed(String text) { throw new IllegalStateException("不得泄露的SQL或原文"); }
            };
            new ProductionTaskExecutionHandler(throwing, service).handle(calculation);
            assertEquals("COMPUTATION_FAILED", context.getBean(TaskTerminalMapper.class).snapshot(calculation.taskId()).errorCode());
            assertEquals(ALREADY_APPLIED, service.fail(calculation, TaskFailure.COMPUTATION_FAILED));
            TaskExecution invalid = claim(context, "input", "原文");
            var invalidInput = new TaskExecution(invalid.taskId(), invalid.documentId(), invalid.runToken(), invalid.embeddingVersion(), 1, "\ud800", "default");
            new ProductionTaskExecutionHandler(actual, service).handle(invalidInput);
            assertEquals("INPUT_INVALID", context.getBean(TaskTerminalMapper.class).snapshot(invalid.taskId()).errorCode());
            TaskExecution mismatch = claim(context, "version", "原文");
            new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE embedding_tasks SET embedding_version='old' WHERE task_id=?", mismatch.taskId());
            var oldVersion = new TaskExecution(mismatch.taskId(), mismatch.documentId(), mismatch.runToken(), "old", 1, "原文", "default");
            new ProductionTaskExecutionHandler(actual, service).handle(oldVersion);
            var snapshot = context.getBean(TaskTerminalMapper.class).snapshot(mismatch.taskId());
            assertEquals("FAILED", snapshot.status());
            assertEquals("VERSION_MISMATCH", snapshot.errorCode());
            assertNotNull(snapshot.finishedAt());
            assertNull(snapshot.vectorBytes());
        }
    }

    @Test
    void 明确回滚的暂时数据库错误最多三次可成功() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "retry", "");
            AtomicInteger calls = new AtomicInteger();
            var mapper = decorate(context.getBean(TaskTerminalMapper.class), name -> {
                if (name.equals("writeVector") && calls.incrementAndGet() <= 2) { throw new CannotAcquireLockException("测试锁竞争"); }
            });
            assertEquals(APPLIED, service(context, mapper, context.getBean(PlatformTransactionManager.class)).succeed(execution, new float[256]));
            assertEquals(3, calls.get());
            assertEquals("SUCCEEDED", context.getBean(KnowledgeMapper.class).findTask(execution.taskId()).status());
        }
    }

    @Test
    void 提交后抛错独立确认完整向量终态不重复写() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "confirmed", "真实");
            AtomicBoolean once = new AtomicBoolean(true);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (once.compareAndSet(true, false)) { throw new TransactionSystemException("提交后测试异常"); }
                }
            };
            float[] vector = context.getBean(TextEmbedder.class).embed("真实");
            assertEquals(ALREADY_APPLIED, service(context, context.getBean(TaskTerminalMapper.class), manager).succeed(execution, vector));
            assertArrayEquals(VectorCodec.encode(vector), context.getBean(TaskTerminalMapper.class).snapshot(execution.taskId()).vectorBytes());
            assertTrue(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 相同长度不同向量不能冒充未知提交成功证据() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "mismatch", "");
            AtomicBoolean once = new AtomicBoolean(true);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (once.compareAndSet(true, false)) {
                        float[] wrong = new float[256];
                        wrong[0] = 1;
                        new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE documents SET vector_bytes=? WHERE document_id='mismatch'", VectorCodec.encode(wrong));
                        throw new TransactionSystemException("提交后测试异常");
                    }
                }
            };
            assertThrows(BusinessException.class, () -> service(context, context.getBean(TaskTerminalMapper.class), manager)
                    .succeed(execution, new float[256]));
            assertFalse(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 写许可关闭后迟到计算结果不进入任何SQL() {
        try (var context = B2TestSupport.start(directory)) {
            TaskExecution execution = claim(context, "closed", "");
            TaskWriteBarrier barrier = new TaskWriteBarrier();
            barrier.closeAndDrain();
            var mapper = decorate(context.getBean(TaskTerminalMapper.class), name -> { throw new AssertionError("关闭后不得执行SQL"); });
            var service = new TaskTerminalService(mapper, context.getBean(TaskReadiness.class), barrier, context.getBean(PlatformTransactionManager.class));
            assertEquals(WRITE_CLOSED, service.succeed(execution, new float[256]));
            assertEquals(WRITE_CLOSED, service.fail(execution, TaskFailure.COMPUTATION_FAILED));
            assertEquals("RUNNING", context.getBean(KnowledgeMapper.class).findTask(execution.taskId()).status());
        }
    }

    private static TaskExecution claim(ConfigurableApplicationContext context, String id, String text) {
        String taskId = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest(id, text, null)).taskId();
        return context.getBean(TaskClaimService.class).claim(taskId).orElseThrow();
    }

    private static TaskTerminalService service(ConfigurableApplicationContext context, TaskTerminalMapper mapper, PlatformTransactionManager manager) {
        return new TaskTerminalService(mapper, context.getBean(TaskReadiness.class), new TaskWriteBarrier(), manager);
    }

    private static TaskTerminalMapper decorate(TaskTerminalMapper actual, Consumer<String> before) {
        return (TaskTerminalMapper) Proxy.newProxyInstance(TaskTerminalMapper.class.getClassLoader(), new Class<?>[] {TaskTerminalMapper.class},
                (proxy, method, arguments) -> {
                    before.accept(method.getName());
                    try { return method.invoke(actual, arguments); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
    }

    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager actual;
        Manager(PlatformTransactionManager actual) { this.actual = actual; }
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actual.getTransaction(definition); }
        @Override public void commit(TransactionStatus status) { actual.commit(status); }
        @Override public void rollback(TransactionStatus status) { actual.rollback(status); }
    }
}
