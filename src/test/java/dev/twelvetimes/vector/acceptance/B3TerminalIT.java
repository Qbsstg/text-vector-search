package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.persistence.TerminalSnapshot;
import dev.twelvetimes.vector.task.ProductionTaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskExecution;
import dev.twelvetimes.vector.task.TaskFailure;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskTerminalService;
import dev.twelvetimes.vector.task.TaskTerminalService.TerminalResult;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 以隔离组成准备RUNNING夹具，实际调用生产终态与handler；生产自动装配另行独立验收。 */
class B3TerminalIT {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"", "ababa"})
    void 生产handler调用真实B1并原子保存黄金向量(String text) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String taskId = env.context.getBean(dev.twelvetimes.vector.document.DocumentAdmissionService.class)
                    .submit(new dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest("golden", text, null)).taskId();
            var execution = env.bean(TaskClaimService.class).claim(taskId).orElseThrow();
            var handler = new ProductionTaskExecutionHandler(env.bean(TextEmbedder.class), terminals(env));
            handler.handle(execution);
            var result = snapshot(env, taskId);
            assertEquals("SUCCEEDED", result.status());
            assertEquals(result.finishedAt(), result.vectorCompletedAt());
            assertNotNull(result.finishedAt());
            assertEquals(execution.embeddingVersion(), result.documentVersion());
            assertEquals(1024, result.vectorBytes().length);
            String expected = text.isEmpty() ? "5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"
                    : "ad82c864c2dec3476778eeaf5f6e83a64d5827b9f029f22737e7e33523125ad1";
            assertEquals(expected, HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(result.vectorBytes())));
            if (text.isEmpty()) {
                assertArrayEquals(new byte[1024], result.vectorBytes());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"第二步前异常", "第二步后异常", "更新返回零行"})
    void 成功两表写入任一步失败都不能半提交(String mode) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = claim(env, "rollback");
            AtomicInteger taskUpdates = new AtomicInteger();
            var mapper = mapper(env, (name, args, proceed) -> {
                if (name.equals("succeed")) {
                    taskUpdates.incrementAndGet();
                }
                if (name.equals("writeVector")) {
                    if (mode.equals("更新返回零行")) {
                        return 0;
                    }
                    if (mode.equals("第二步后异常")) {
                        proceed.call();
                    }
                    throw new DataAccessResourceFailureException("仅测试：故障SQL不应泄露");
                }
                return proceed.call();
            });
            var failure = assertThrows(BusinessException.class,
                    () -> terminals(env, mapper, env.bean(PlatformTransactionManager.class))
                            .succeed(execution, env.bean(TextEmbedder.class).embed("ababa")));
            assertEquals("RESULT_UNKNOWN", failure.code());
            assertEquals(1, taskUpdates.get(), "非暂时错误不盲重试");
            var persisted = snapshot(env, execution.taskId());
            assertEquals("RUNNING", persisted.status());
            assertNull(persisted.finishedAt());
            assertNull(persisted.vectorBytes());
            assertNull(persisted.vectorCompletedAt());
            assertFalse(env.bean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 旧令牌成功失败均拒写且本次终态重放不覆盖首次结果() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var old = claim(env, "tokens");
            env.sql("UPDATE embedding_tasks SET run_token='current-token',attempt_count=attempt_count+1 WHERE task_id=?", old.taskId());
            var current = new TaskExecution(old.taskId(), old.documentId(), "current-token", old.embeddingVersion(),
                    old.attemptCount() + 1, old.text(), old.channel());
            var service = terminals(env);
            float[] oldVector = env.bean(TextEmbedder.class).embed("old");
            float[] goodVector = env.bean(TextEmbedder.class).embed("ababa");
            assertEquals(TerminalResult.STALE, service.succeed(old, oldVector));
            assertEquals(TerminalResult.STALE, service.fail(old, TaskFailure.COMPUTATION_FAILED));
            assertNull(snapshot(env, old.taskId()).vectorBytes());
            assertEquals("RUNNING", snapshot(env, old.taskId()).status());
            assertEquals(TerminalResult.APPLIED, service.succeed(current, goodVector));
            var first = snapshot(env, old.taskId());
            assertEquals(TerminalResult.ALREADY_APPLIED, service.succeed(current, goodVector));
            assertEquals(TerminalResult.STALE, service.succeed(old, oldVector));
            assertEquals(TerminalResult.STALE, service.fail(old, TaskFailure.INPUT_INVALID));
            assertEquals(TerminalResult.STALE, service.fail(current, TaskFailure.INPUT_INVALID));
            var finalState = snapshot(env, old.taskId());
            assertEquals(first.finishedAt(), finalState.finishedAt());
            assertEquals(first.vectorCompletedAt(), finalState.vectorCompletedAt());
            assertArrayEquals(first.vectorBytes(), finalState.vectorBytes());
            assertNull(finalState.errorCode());
        }
    }

    @Test
    void 计算暂停期间失效完成后成功回写不复活或覆盖计数() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = claim(env, "invalidated");
            env.sql("UPDATE documents SET hit_count=9007199254740993 WHERE document_id=?", execution.documentId());
            CountDownLatch computed = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var original = env.bean(TextEmbedder.class);
            TextEmbedder controlled = new TextEmbedder() {
                @Override
                public float[] embed(String text) {
                    float[] result = original.embed(text);
                    computed.countDown();
                    try {
                        await(release);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    return result;
                }
                @Override
                public String version() { return original.version(); }
            };
            var executor = Executors.newSingleThreadExecutor();
            try {
                var future = executor.submit(() -> new ProductionTaskExecutionHandler(controlled, terminals(env)).handle(execution));
                await(computed);
                env.sql("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP WHERE document_id=?", execution.documentId());
                var invalidated = env.bean(KnowledgeMapper.class).findDocument(execution.documentId());
                assertTrue(invalidated.invalidated());
                release.countDown();
                future.get(8, TimeUnit.SECONDS);
                var finalState = env.bean(KnowledgeMapper.class).findDocument(execution.documentId());
                assertEquals("SUCCEEDED", finalState.status());
                assertTrue(finalState.invalidated());
                assertEquals(invalidated.invalidatedAt(), finalState.invalidatedAt());
                assertEquals(invalidated.text(), finalState.text());
                assertEquals(9007199254740993L, finalState.hitCount());
            } finally {
                release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"VERSION_MISMATCH", "INPUT_INVALID", "COMPUTATION_FAILED"})
    void 失败分类可靠持久化且不泄露异常原文(String mode) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("failure");
            if (mode.equals("VERSION_MISMATCH")) {
                env.sql("UPDATE embedding_tasks SET embedding_version='old-version' WHERE task_id=?", task);
            }
            TaskExecution execution = env.bean(TaskClaimService.class).claim(task).orElseThrow();
            TextEmbedder original = env.bean(TextEmbedder.class);
            if (mode.equals("INPUT_INVALID")) {
                execution = new TaskExecution(execution.taskId(), execution.documentId(), execution.runToken(),
                        execution.embeddingVersion(), execution.attemptCount(), "\ud800", execution.channel());
            }
            TextEmbedder selected = mode.equals("COMPUTATION_FAILED") ? new TextEmbedder() {
                @Override
                public float[] embed(String text) { throw new IllegalStateException("secret SQL/path/user-input"); }
                @Override
                public String version() { return original.version(); }
            } : original;
            var service = terminals(env);
            new ProductionTaskExecutionHandler(selected, service).handle(execution);
            var first = snapshot(env, task);
            assertEquals("FAILED", first.status());
            assertEquals(mode, first.errorCode());
            assertTrue(first.errorCode().length() <= 64);
            assertTrue(first.errorMessage().length() <= 512);
            assertFalse(first.errorMessage().contains("secret"));
            assertFalse(first.errorMessage().contains("SQL"));
            assertNull(first.vectorBytes());
            assertNotNull(first.finishedAt());
            assertEquals(TerminalResult.ALREADY_APPLIED, service.fail(execution,
                    new TaskFailure(first.errorCode(), first.errorMessage())));
            assertEquals(first.finishedAt(), snapshot(env, task).finishedAt());
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"精确成功", "相同长度内容不同", "确认读取失败"})
    void 真提交后抛错必须核对完整终态向量且不能反写FAILED(String mode) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = claim(env, "unknown");
            AtomicBoolean committed = new AtomicBoolean();
            AtomicInteger updates = new AtomicInteger();
            AtomicInteger confirms = new AtomicInteger();
            var decorated = mapper(env, (name, args, proceed) -> {
                if (name.equals("succeed") || name.equals("fail")) {
                    updates.incrementAndGet();
                }
                if (name.equals("snapshot") && committed.get()) {
                    confirms.incrementAndGet();
                    if (mode.equals("确认读取失败")) {
                        throw new DataAccessResourceFailureException("仅测试：确认不可用");
                    }
                }
                return proceed.call();
            });
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (committed.compareAndSet(false, true)) {
                        if (mode.equals("相同长度内容不同")) {
                            try {
                                env.sql("UPDATE documents SET vector_bytes=? WHERE document_id=?", new byte[1024], execution.documentId());
                            } catch (Exception exception) {
                                throw new AssertionError(exception);
                            }
                        }
                        throw new TransactionSystemException("仅测试：实际commit之后异常");
                    }
                }
            };
            var service = terminals(env, decorated, manager);
            float[] vector = env.bean(TextEmbedder.class).embed("ababa");
            if (mode.equals("精确成功")) {
                assertEquals(TerminalResult.ALREADY_APPLIED, service.succeed(execution, vector));
                assertArrayEquals(VectorCodec.encode(vector), snapshot(env, execution.taskId()).vectorBytes());
                assertTrue(env.bean(TaskReadiness.class).accepting());
            } else {
                assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                        () -> service.succeed(execution, vector)).code());
                assertFalse(env.bean(TaskReadiness.class).accepting());
            }
            assertEquals(1, updates.get());
            assertTrue(confirms.get() >= 1 && confirms.get() <= 3);
            assertEquals("SUCCEEDED", snapshot(env, execution.taskId()).status());
            assertNull(snapshot(env, execution.taskId()).errorCode());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {2, 3})
    void 明确回滚的暂时错误最多三次且耗尽不伪造FAILED(int failures) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = claim(env, "retry");
            AtomicInteger attempts = new AtomicInteger();
            var mapper = mapper(env, (name, args, proceed) -> {
                if (name.equals("writeVector") && attempts.incrementAndGet() <= failures) {
                    throw new TransientDataAccessResourceException("仅测试：明确回滚的暂时故障");
                }
                return proceed.call();
            });
            var service = terminals(env, mapper, env.bean(PlatformTransactionManager.class));
            if (failures == 2) {
                assertEquals(TerminalResult.APPLIED, service.succeed(execution, env.bean(TextEmbedder.class).embed("ababa")));
                assertEquals("SUCCEEDED", snapshot(env, execution.taskId()).status());
            } else {
                assertThrows(BusinessException.class, () -> service.succeed(execution, new float[256]));
                assertEquals("RUNNING", snapshot(env, execution.taskId()).status());
                assertNull(snapshot(env, execution.taskId()).vectorBytes());
                assertFalse(env.bean(TaskReadiness.class).accepting());
            }
            assertEquals(3, attempts.get());
        }
    }

    @Test
    void 写许可关闭后迟到成功失败均不访问Mapper() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = claim(env, "closed");
            AtomicInteger databaseCalls = new AtomicInteger();
            var mapper = mapper(env, (name, args, proceed) -> {
                databaseCalls.incrementAndGet();
                throw new AssertionError("关闭后不得访问数据库");
            });
            var readiness = env.bean(TaskReadiness.class);
            readiness.writeBarrier().closeAndDrain();
            var service = terminals(env, mapper, env.bean(PlatformTransactionManager.class));
            assertEquals(TerminalResult.WRITE_CLOSED, service.succeed(execution, new float[256]));
            assertEquals(TerminalResult.WRITE_CLOSED, service.fail(execution, TaskFailure.COMPUTATION_FAILED));
            assertEquals(0, databaseCalls.get());
            assertEquals("RUNNING", snapshot(env, execution.taskId()).status());
        }
    }

    private static TaskExecution claim(B2QaEnvironment env, String id) {
        return env.bean(TaskClaimService.class).claim(env.submit(id)).orElseThrow();
    }

    private static TerminalSnapshot snapshot(B2QaEnvironment env, String id) {
        return env.bean(TaskTerminalMapper.class).snapshot(id);
    }

    private static TaskTerminalService terminals(B2QaEnvironment env) {
        return terminals(env, env.bean(TaskTerminalMapper.class), env.bean(PlatformTransactionManager.class));
    }

    private static TaskTerminalService terminals(B2QaEnvironment env, TaskTerminalMapper mapper, PlatformTransactionManager manager) {
        TaskReadiness readiness = env.bean(TaskReadiness.class);
        return new TaskTerminalService(mapper, readiness, readiness.writeBarrier(), manager);
    }

    private static TaskTerminalMapper mapper(B2QaEnvironment env, B2QaEnvironment.Interceptor interceptor) {
        TaskTerminalMapper actual = env.bean(TaskTerminalMapper.class);
        return (TaskTerminalMapper) Proxy.newProxyInstance(TaskTerminalMapper.class.getClassLoader(),
                new Class<?>[]{TaskTerminalMapper.class}, (proxy, method, args) -> interceptor.invoke(method.getName(), args, () -> {
                    try {
                        return method.invoke(actual, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
    }
}
