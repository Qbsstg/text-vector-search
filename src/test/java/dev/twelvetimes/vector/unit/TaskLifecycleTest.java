package dev.twelvetimes.vector.unit;

import com.zaxxer.hikari.HikariDataSource;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.ProductionTaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskTerminalService;
import dev.twelvetimes.vector.task.TaskWriteBarrier;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TaskLifecycleTest {
    @TempDir Path directory;

    @Test
    void 正常停止先拒接等待计算与终态提交再关闭许可() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            var admission = context.getBean(DocumentAdmissionService.class);
            String id = admission.submit(new SubmitDocumentRequest("normal", "真实计算🙂", null)).taskId();
            var readiness = context.getBean(TaskReadiness.class);
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            TextEmbedder actual = context.getBean(TextEmbedder.class);
            var terminals = new TaskTerminalService(context.getBean(TaskTerminalMapper.class), readiness,
                    readiness.writeBarrier(), context.getBean(PlatformTransactionManager.class));
            var handler = new ProductionTaskExecutionHandler(blocked(actual, entered, release), terminals);
            var executor = Executors.newFixedThreadPool(2);
            try (var dispatcher = new TaskDispatcher(context.getBean(KnowledgeMapper.class), context.getBean(TaskClaimService.class),
                    readiness, context.getBean(VectorProperties.class), handler)) {
                try {
                    dispatcher.scanOnce();
                    assertTrue(entered.await(5, TimeUnit.SECONDS));
                    var firstClose = executor.submit(dispatcher::close);
                    TaskRecoveryServiceTest.until(() -> readiness.state() == TaskReadiness.State.STOPPING);
                    var secondClose = executor.submit(dispatcher::close);
                    assertTrue(readiness.writeBarrier().isOpen());
                    assertFalse(firstClose.isDone());
                    assertFalse(secondClose.isDone());
                    assertThrows(BusinessException.class, () -> admission.submit(new SubmitDocumentRequest("late", "", null)));
                    release.countDown();
                    firstClose.get(5, TimeUnit.SECONDS);
                    secondClose.get(5, TimeUnit.SECONDS);
                    assertEquals(TaskReadiness.State.STOPPED, readiness.state());
                    assertFalse(readiness.writeBarrier().isOpen());
                    assertEquals("SUCCEEDED", context.getBean(KnowledgeMapper.class).findTask(id).status());
                } finally { release.countDown(); executor.shutdownNow(); }
            }
        }
    }

    @Test
    void CPU超时后数据源已销毁仍不得进入终态SQL且保留RUNNING待重启() throws Exception {
        var context = B2TestSupport.start(directory);
        var dataSource = (HikariDataSource) context.getBean(DataSource.class);
        var readiness = context.getBean(TaskReadiness.class);
        String id = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("timeout", "晚到计算🙂", null)).taskId();
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch returned = new CountDownLatch(1);
        AtomicInteger sqlCalls = new AtomicInteger();
        var guarded = (TaskTerminalMapper) Proxy.newProxyInstance(TaskTerminalMapper.class.getClassLoader(),
                new Class<?>[] {TaskTerminalMapper.class}, (proxy, method, arguments) -> {
                    sqlCalls.incrementAndGet();
                    throw new AssertionError("关闭许可后不得调用终态SQL");
                });
        var terminals = new TaskTerminalService(guarded, readiness, readiness.writeBarrier(), context.getBean(PlatformTransactionManager.class));
        var handler = new ProductionTaskExecutionHandler(blocked(context.getBean(TextEmbedder.class), entered, release), terminals);
        var props = new VectorProperties(1, 1, 10, 20, 1, 20000, 262144, 100, 100, 32, 500, 32, 1);
        var dispatcher = new TaskDispatcher(context.getBean(KnowledgeMapper.class), context.getBean(TaskClaimService.class), readiness,
                props, execution -> { try { handler.handle(execution); } finally { returned.countDown(); } });
        try {
            dispatcher.scanOnce();
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            dispatcher.close();
            assertEquals(TaskReadiness.State.STOPPED, readiness.state());
            assertFalse(readiness.writeBarrier().isOpen());
            assertEquals("RUNNING", context.getBean(KnowledgeMapper.class).findTask(id).status());
            context.close();
            assertTrue(dataSource.isClosed());
            release.countDown();
            assertTrue(returned.await(5, TimeUnit.SECONDS));
            assertEquals(0, sqlCalls.get());
        } finally { release.countDown(); dispatcher.close(); context.close(); }
        try (var restarted = TaskRecoveryServiceTest.production(directory)) {
            TaskRecoveryServiceTest.until(() -> "SUCCEEDED".equals(restarted.getBean(KnowledgeMapper.class).findTask(id).status()));
            assertEquals(2, restarted.getBean(KnowledgeMapper.class).findTask(id).attemptCount());
        }
    }

    @Test
    void 关闭许可必须等待已进入的真实终态物理commit完成() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            String id = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("transaction", "", null)).taskId();
            var execution = context.getBean(TaskClaimService.class).claim(id).orElseThrow();
            var barrier = new TaskWriteBarrier();
            var actual = context.getBean(PlatformTransactionManager.class);
            CountDownLatch beforeCommit = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var manager = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actual.getTransaction(definition); }
                @Override public void rollback(TransactionStatus status) { actual.rollback(status); }
                @Override public void commit(TransactionStatus status) {
                    beforeCommit.countDown();
                    await(release);
                    actual.commit(status);
                }
            };
            var terminals = new TaskTerminalService(context.getBean(TaskTerminalMapper.class), context.getBean(TaskReadiness.class), barrier, manager);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var write = executor.submit(() -> terminals.succeed(execution, new float[256]));
                assertTrue(beforeCommit.await(5, TimeUnit.SECONDS));
                var close = executor.submit(barrier::closeAndDrain);
                TaskRecoveryServiceTest.until(() -> !barrier.isOpen());
                assertFalse(close.isDone());
                release.countDown();
                assertEquals(TaskTerminalService.TerminalResult.APPLIED, write.get(5, TimeUnit.SECONDS));
                close.get(5, TimeUnit.SECONDS);
                assertEquals("SUCCEEDED", context.getBean(KnowledgeMapper.class).findTask(id).status());
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    private static TextEmbedder blocked(TextEmbedder actual, CountDownLatch entered, CountDownLatch release) {
        return new TextEmbedder() {
            @Override public String version() { return actual.version(); }
            @Override public float[] embed(String text) {
                entered.countDown();
                await(release);
                return actual.embed(text);
            }
        };
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
