package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskExecution;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskDispatcherTest {
    @TempDir Path directory;

    @Test
    void 生产无处理器保持QUEUED且不增加尝试次数() {
        try (var context = B2TestSupport.start(directory)) {
            var submission = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("waiting", "", null));
            var dispatcher = context.getBean(TaskDispatcher.class);
            assertEquals(0, dispatcher.scanOnce());
            var row = context.getBean(KnowledgeMapper.class).findTask(submission.taskId());
            assertEquals("QUEUED", row.status());
            assertEquals(0, row.attemptCount());
            assertEquals(0, dispatcher.inFlightCount());
        }
    }

    @Test
    void 独立事务竞争同一任务只有一个领取成功() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            String id = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("race", "原文", "FAQ")).taskId();
            var claims = context.getBean(TaskClaimService.class);
            var executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                var first = executor.submit(() -> { await(start); return claims.claim(id); });
                var second = executor.submit(() -> { await(start); return claims.claim(id); });
                start.countDown();
                var a = first.get(5, TimeUnit.SECONDS);
                var b = second.get(5, TimeUnit.SECONDS);
                assertEquals(1, (a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0));
                TaskExecution result = a.orElseGet(b::orElseThrow);
                var row = context.getBean(KnowledgeMapper.class).findTask(id);
                assertEquals("RUNNING", row.status());
                assertEquals(1, row.attemptCount());
                assertEquals(row.runToken(), result.runToken());
                assertNotNull(row.startedAt());
                assertEquals("原文", result.text());
                assertEquals("FAQ", result.channel());
                assertFalse(claims.claim(id).isPresent());
                assertEquals(1, context.getBean(KnowledgeMapper.class).findTask(id).attemptCount());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test
    void 饱和后持久任务可补扫且处理器调用在事务外() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            var admission = context.getBean(DocumentAdmissionService.class);
            for (String id : List.of("a", "b", "c")) { admission.submit(new SubmitDocumentRequest(id, "", null)); }
            var mapper = context.getBean(KnowledgeMapper.class);
            var readiness = context.getBean(TaskReadiness.class);
            var props = B2TestSupport.properties(20000);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            CountDownLatch secondDone = new CountDownLatch(1);
            CountDownLatch thirdDone = new CountDownLatch(1);
            Set<String> seen = ConcurrentHashMap.newKeySet();
            AtomicReference<Throwable> error = new AtomicReference<>();
            try (var dispatcher = new TaskDispatcher(mapper, context.getBean(TaskClaimService.class), readiness, props, execution -> {
                try {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionSynchronizationManager.hasResource(context.getBean(DataSource.class)));
                    assertTrue(seen.add(execution.documentId()));
                    assertEquals("RUNNING", mapper.findTask(execution.taskId()).status());
                    if (execution.documentId().equals("a")) { entered.countDown(); await(release); }
                    if (execution.documentId().equals("b")) { secondDone.countDown(); }
                    if (execution.documentId().equals("c")) { thirdDone.countDown(); }
                } catch (Throwable failure) { error.set(failure); throw new IllegalStateException(failure); }
            })) {
                try {
                    assertEquals(2, dispatcher.scanOnce());
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    assertEquals(1, dispatcher.queuedCount());
                    assertEquals(2, dispatcher.inFlightCount());
                    assertEquals(0, dispatcher.scanOnce());
                    assertEquals("QUEUED", mapper.findDocument("c").status());
                    assertEquals(0, mapper.findDocument("c").attemptCount());
                    release.countDown();
                    assertTrue(secondDone.await(5, TimeUnit.SECONDS));
                    assertEquals(1, dispatcher.scanOnce());
                    assertTrue(thirdDone.await(5, TimeUnit.SECONDS));
                } finally { release.countDown(); }
            }
            if (error.get() != null) { throw new AssertionError(error.get()); }
            assertEquals(Set.of("a", "b", "c"), seen);
            for (String id : seen) { assertEquals(1, mapper.findDocument(id).attemptCount()); }
        }
    }

    @Test
    void 单次扫描故障不终止后续扫描() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("retry", "", null));
            var actual = context.getBean(KnowledgeMapper.class);
            AtomicBoolean first = new AtomicBoolean(true);
            KnowledgeMapper decorated = decorate(actual, (name) -> {
                if (name.equals("queued") && first.compareAndSet(true, false)) {
                    throw new DataAccessResourceFailureException("扫描测试故障");
                }
            });
            CountDownLatch handled = new CountDownLatch(1);
            try (var dispatcher = new TaskDispatcher(decorated, context.getBean(TaskClaimService.class),
                    context.getBean(TaskReadiness.class), B2TestSupport.properties(20000), execution -> handled.countDown())) {
                assertEquals(0, dispatcher.scanOnce());
                assertEquals(1, dispatcher.scanOnce());
                assertTrue(handled.await(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void 领取真实commit后抛错必须核对本次token才交付执行权() {
        try (var context = B2TestSupport.start(directory)) {
            String id = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("confirmed", "", null)).taskId();
            AtomicBoolean once = new AtomicBoolean(true);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (once.compareAndSet(true, false)) { throw new TransactionSystemException("提交后测试异常"); }
                }
            };
            var mapper = context.getBean(KnowledgeMapper.class);
            TaskExecution execution = new TaskClaimService(mapper, context.getBean(TaskReadiness.class), manager).claim(id).orElseThrow();
            assertEquals(mapper.findTask(id).runToken(), execution.runToken());
            assertEquals(1, execution.attemptCount());
        }
    }

    @Test
    void 领取提交未知且独立查询失败关闭接收门禁并保留原状态() {
        try (var context = B2TestSupport.start(directory)) {
            var admission = context.getBean(DocumentAdmissionService.class);
            String id = admission.submit(new SubmitDocumentRequest("unknown", "", null)).taskId();
            AtomicBoolean committed = new AtomicBoolean();
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    committed.set(true);
                    throw new TransactionSystemException("提交后测试异常");
                }
            };
            var mapper = decorate(context.getBean(KnowledgeMapper.class), name -> {
                if (name.equals("findTask") && committed.get()) { throw new DataAccessResourceFailureException("确认查询故障"); }
            });
            var readiness = context.getBean(TaskReadiness.class);
            assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                    () -> new TaskClaimService(mapper, readiness, manager).claim(id)).code());
            assertFalse(readiness.accepting());
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> admission.submit(new SubmitDocumentRequest("later", "", null))).code());
            assertEquals("RUNNING", context.getBean(KnowledgeMapper.class).findTask(id).status());
        }
    }

    @Test
    void 旧RUNNING重启不假恢复且拒绝新接收() {
        try (var context = B2TestSupport.start(directory)) {
            var submission = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("old", "", null));
            context.getBean(TaskClaimService.class).claim(submission.taskId());
        }
        try (var restarted = B2TestSupport.start(directory)) {
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> restarted.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("new", "", null))).code());
            assertEquals("RUNNING", restarted.getBean(KnowledgeMapper.class).findDocument("old").status());
            assertEquals(1, restarted.getBean(KnowledgeMapper.class).findDocument("old").attemptCount());
        }
    }

    @Test
    void 文档总量包含失效记录且与待处理容量独立() {
        try (var context = B2TestSupport.start(directory, "--vector.max-documents=1", "--vector.max-pending-tasks=10")) {
            var service = context.getBean(DocumentAdmissionService.class);
            service.submit(new SubmitDocumentRequest("full", "", null));
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.update("UPDATE embedding_tasks SET status='FAILED'");
            jdbc.update("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP");
            assertEquals(0, context.getBean(KnowledgeMapper.class).countPending());
            assertEquals("CAPACITY_EXCEEDED", assertThrows(BusinessException.class,
                    () -> service.submit(new SubmitDocumentRequest("extra", "", null))).code());
            assertEquals(1, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 接收提交未知且确认失败不重插并关闭门禁() {
        try (var context = B2TestSupport.start(directory)) {
            AtomicBoolean committed = new AtomicBoolean();
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    committed.set(true);
                    throw new TransactionSystemException("提交后故障");
                }
            };
            var mapper = decorate(context.getBean(KnowledgeMapper.class), name -> {
                if (name.equals("findTask") && committed.get()) { throw new DataAccessResourceFailureException("确认故障"); }
            });
            var readiness = context.getBean(TaskReadiness.class);
            var service = new DocumentAdmissionService(mapper, context.getBean(RequestValidator.class),
                    context.getBean(VectorProperties.class), context.getBean(TextEmbedder.class), readiness, manager);
            assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                    () -> service.submit(new SubmitDocumentRequest("unknown", "", null))).code());
            assertFalse(readiness.accepting());
            assertEquals(1, context.getBean(KnowledgeMapper.class).countDocuments());
            assertEquals(1, context.getBean(KnowledgeMapper.class).countPending());
        }
    }

    private static KnowledgeMapper decorate(KnowledgeMapper delegate, Consumer<String> before) {
        return (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                new Class<?>[] {KnowledgeMapper.class}, (proxy, method, args) -> {
                    before.accept(method.getName());
                    try { return method.invoke(delegate, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                });
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;
        Manager(PlatformTransactionManager delegate) { this.delegate = delegate; }
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return delegate.getTransaction(definition); }
        @Override public void commit(TransactionStatus status) { delegate.commit(status); }
        @Override public void rollback(TransactionStatus status) { delegate.rollback(status); }
    }
}
