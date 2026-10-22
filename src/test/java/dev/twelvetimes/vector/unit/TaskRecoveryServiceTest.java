package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskRecoveryMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.ReadinessFilter;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskRecoveryService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

class TaskRecoveryServiceTest {
    @TempDir Path directory;

    @Test
    void 真实生产组成自动执行正常及空文且成功任务重启不重做() {
        String task;
        try (var context = production(directory)) {
            assertEquals(TaskReadiness.State.READY, context.getBean(TaskReadiness.class).state());
            var admission = context.getBean(DocumentAdmissionService.class);
            for (String text : new String[] {"", "中文恢复🙂"}) {
                task = admission.submit(new SubmitDocumentRequest("d" + text.length(), text, null)).taskId();
                String id = task;
                var mapper = context.getBean(KnowledgeMapper.class);
                until(() -> "SUCCEEDED".equals(mapper.findTask(id).status()));
                var snapshot = context.getBean(TaskTerminalMapper.class).snapshot(task);
                assertArrayEquals(VectorCodec.encode(context.getBean(TextEmbedder.class).embed(text)), snapshot.vectorBytes());
                assertEquals(1, mapper.findTask(task).attemptCount());
            }
        }
        try (var restarted = production(directory)) {
            assertEquals(0, restarted.getBean(KnowledgeMapper.class).countPending());
            assertEquals(1, restarted.getBean(KnowledgeMapper.class).findDocument("d0").attemptCount());
        }
    }

    @Test
    void 旧RUNNING启动恢复后真实执行且保留失效计数和提交元信息() {
        String task;
        try (var previous = B2TestSupport.start(directory)) {
            task = previous.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("old", "旧任务🙂", "FAQ")).taskId();
            previous.getBean(TaskClaimService.class).claim(task);
            new JdbcTemplate(previous.getBean(DataSource.class)).update("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP,hit_count=7 WHERE document_id='old'");
        }
        try (var context = production(directory)) {
            var mapper = context.getBean(KnowledgeMapper.class);
            until(() -> "SUCCEEDED".equals(mapper.findTask(task).status()));
            var row = mapper.findTask(task);
            assertEquals(2, row.attemptCount());
            assertTrue(row.invalidated());
            assertEquals(7, row.hitCount());
            assertEquals("FAQ", row.channel());
            assertEquals("旧任务🙂", row.text());
        }
    }

    @Test
    void 恢复实际commit前提交与扫描均拒绝并且字段清理只在commit后可见() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            String task = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("old", "", null)).taskId();
            context.getBean(TaskClaimService.class).claim(task);
            var mapper = context.getBean(KnowledgeMapper.class);
            var readiness = new TaskReadiness(mapper);
            CountDownLatch beforeCommit = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    beforeCommit.countDown();
                    await(release);
                    super.commit(status);
                }
            };
            var recovery = new TaskRecoveryService(context.getBean(TaskRecoveryMapper.class), mapper, readiness, manager);
            var executor = Executors.newSingleThreadExecutor();
            try (var dispatcher = new TaskDispatcher(mapper, new TaskClaimService(mapper, readiness, manager), readiness,
                    B2TestSupport.properties(20000), execution -> fail("恢复前不得执行"))) {
                try {
                    var work = executor.submit(recovery::recover);
                    assertTrue(beforeCommit.await(5, TimeUnit.SECONDS));
                    assertEquals(TaskReadiness.State.RECOVERING, readiness.state());
                    assertEquals(0, dispatcher.scanOnce());
                    var admission = new DocumentAdmissionService(mapper, context.getBean(RequestValidator.class), context.getBean(VectorProperties.class),
                            context.getBean(TextEmbedder.class), readiness, context.getBean(PlatformTransactionManager.class));
                    assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                            () -> admission.submit(new SubmitDocumentRequest("new", "", null))).code());
                    assertEquals("RUNNING", mapper.findTask(task).status());
                    release.countDown();
                    assertEquals(1, work.get(5, TimeUnit.SECONDS));
                    assertTrue(readiness.accepting());
                    var row = mapper.findTask(task);
                    assertEquals("QUEUED", row.status());
                    assertEquals(1, row.attemptCount());
                    assertNull(row.runToken());
                    assertNull(row.startedAt());
                } finally { release.countDown(); executor.shutdownNow(); }
            }
        }
    }

    @Test
    void 恢复失败不能READY且真正过滤业务请求() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            var readiness = new TaskReadiness(context.getBean(KnowledgeMapper.class));
            TaskRecoveryMapper actual = context.getBean(TaskRecoveryMapper.class);
            var broken = (TaskRecoveryMapper) Proxy.newProxyInstance(TaskRecoveryMapper.class.getClassLoader(),
                    new Class<?>[] {TaskRecoveryMapper.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("requeueRunning")) { throw new DataAccessResourceFailureException("测试故障"); }
                        try { return method.invoke(actual, arguments); }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
            new TaskRecoveryService(broken, context.getBean(KnowledgeMapper.class), readiness, context.getBean(PlatformTransactionManager.class)).recover();
            assertEquals(TaskReadiness.State.DEGRADED, readiness.state());
            var filter = new ReadinessFilter(readiness, new ObjectMapper());
            assertEquals(503, filtered(filter, "POST"));
            assertEquals(204, filtered(filter, "GET"));
            readiness.stopping();
            assertEquals(503, filtered(filter, "GET"));
            var recovering = new ReadinessFilter(new TaskReadiness(context.getBean(KnowledgeMapper.class)), new ObjectMapper());
            assertEquals(503, filtered(recovering, "GET"));
        }
    }

    @Test
    void 恢复提交后抛错独立确认而不再次恢复() {
        try (var context = B2TestSupport.start(directory)) {
            String task = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("old", "", null)).taskId();
            context.getBean(TaskClaimService.class).claim(task);
            var readiness = new TaskReadiness(context.getBean(KnowledgeMapper.class));
            AtomicBoolean once = new AtomicBoolean(true);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (once.compareAndSet(true, false)) { throw new TransactionSystemException("提交后测试异常"); }
                }
            };
            assertEquals(1, new TaskRecoveryService(context.getBean(TaskRecoveryMapper.class), context.getBean(KnowledgeMapper.class), readiness, manager).recover());
            assertTrue(readiness.accepting());
            assertEquals(1, context.getBean(KnowledgeMapper.class).findTask(task).attemptCount());
        }
    }

    @Test
    void 两表提交时间不同时恢复未知结果须核对任务自身时间() {
        try (var context = B2TestSupport.start(directory)) {
            String task = context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("different-times", "原文", null)).taskId();
            context.getBean(TaskClaimService.class).claim(task);
            Instant documentTime = Instant.parse("2026-10-20T00:00:00Z");
            Instant taskTime = Instant.parse("2026-10-21T00:00:00Z");
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.update("UPDATE documents SET submitted_at=? WHERE document_id='different-times'", OffsetDateTime.ofInstant(documentTime, ZoneOffset.UTC));
            jdbc.update("UPDATE embedding_tasks SET submitted_at=?,attempt_count=7 WHERE task_id=?", OffsetDateTime.ofInstant(taskTime, ZoneOffset.UTC), task);
            AtomicBoolean committed = new AtomicBoolean();
            AtomicInteger resets = new AtomicInteger();
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (committed.compareAndSet(false, true)) { throw new TransactionSystemException("真实提交后注入未知结果"); }
                }
            };
            TaskRecoveryMapper actual = context.getBean(TaskRecoveryMapper.class);
            var decorated = (TaskRecoveryMapper) Proxy.newProxyInstance(TaskRecoveryMapper.class.getClassLoader(),
                    new Class<?>[] {TaskRecoveryMapper.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("requeueRunning")) { resets.incrementAndGet(); }
                        try { return method.invoke(actual, arguments); }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
            var readiness = new TaskReadiness(context.getBean(KnowledgeMapper.class));
            int recovered = new TaskRecoveryService(decorated, context.getBean(KnowledgeMapper.class), readiness, manager).recover();
            assertTrue(committed.get());
            var row = context.getBean(KnowledgeMapper.class).findTask(task);
            assertEquals("QUEUED", row.status(), "确认前恢复事务已真实提交");
            assertEquals(1, recovered, "不能把文档时间误当成任务时间而拒绝已提交的恢复");
            assertEquals(TaskReadiness.State.READY, readiness.state());
            assertEquals(7, row.attemptCount());
            assertEquals(documentTime, row.submittedAt());
            assertEquals(taskTime, jdbc.queryForObject("SELECT submitted_at FROM embedding_tasks WHERE task_id=?", OffsetDateTime.class, task).toInstant());
            assertNull(row.runToken());
            assertNull(row.startedAt());
            assertEquals(1, resets.get(), "未知结果只独立确认，不重复恢复写入");
        }
    }

    static ConfigurableApplicationContext production(Path path, String... extra) {
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:file:" + path.toAbsolutePath() + "/knowledge;WRITE_DELAY=0",
                "--logging.level.root=ERROR", "--spring.main.banner-mode=off"));
        args.addAll(List.of(extra));
        return new SpringApplication(VectorSearchApplication.class).run(args.toArray(String[]::new));
    }

    private static int filtered(ReadinessFilter filter, String method) throws Exception {
        var request = new MockHttpServletRequest(method, "/api/documents");
        var response = new MockHttpServletResponse();
        filter.doFilter(request, response, (req, res) -> ((MockHttpServletResponse) res).setStatus(204));
        return response.getStatus();
    }

    static void until(BooleanSupplier predicate) {
        long end = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < end) {
            if (predicate.getAsBoolean()) { return; }
            Thread.yield();
        }
        fail("等待持久状态超时");
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager actual;
        Manager(PlatformTransactionManager actual) { this.actual = actual; }
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actual.getTransaction(definition); }
        @Override public void commit(TransactionStatus status) { actual.commit(status); }
        @Override public void rollback(TransactionStatus status) { actual.rollback(status); }
    }
}
