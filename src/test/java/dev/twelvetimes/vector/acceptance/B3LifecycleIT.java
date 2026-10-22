package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskRecoveryMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskRecoveryService;
import dev.twelvetimes.vector.task.TaskTerminalService;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 启动门禁按物理提交/READY/扫描顺序证明；连接器未监听时不虚构HTTP503。 */
class B3LifecycleIT {
    @TempDir Path directory;
    private static volatile Gate gate;

    @Test
    void 恢复真实提交前拒接不派发且恢复后真实B1完成保持失效计数() throws Exception {
        String task;
        Instant submitted;
        try (var seed = new B2QaEnvironment(directory, false)) {
            task = seed.bean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("old", "ababa", null)).taskId();
            seed.bean(TaskClaimService.class).claim(task).orElseThrow();
            seed.sql("UPDATE embedding_tasks SET attempt_count=7,error_code='OLD_ERROR',error_message='旧错误' WHERE task_id=?", task);
            seed.sql("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP,hit_count=9007199254740993 WHERE document_id='old'");
            submitted = seed.bean(KnowledgeMapper.class).findTask(task).submittedAt();
        }
        Gate observed = new Gate();
        gate = observed;
        var executor = Executors.newSingleThreadExecutor();
        ConfigurableApplicationContext context = null;
        try {
            var starting = executor.submit(() -> start(true));
            await(observed.beforeCommit);
            assertEquals(TaskReadiness.State.RECOVERING, observed.readiness.state());
            assertNotNull(observed.admission);
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> observed.admission.submit(new SubmitDocumentRequest("too-early", "", null))).code());
            assertEquals(0, observed.dispatcher.scanOnce());
            assertTrue(observed.events.isEmpty(), "恢复尚未commit，不能发生扫描或领取");
            observed.release.countDown();
            context = starting.get(15, TimeUnit.SECONDS);
            var mapper = context.getBean(KnowledgeMapper.class);
            until(() -> "SUCCEEDED".equals(mapper.findTask(task).status()));
            var completed = mapper.findTask(task);
            assertEquals(8, completed.attemptCount());
            assertEquals(submitted, completed.submittedAt());
            assertTrue(completed.invalidated());
            assertNotNull(completed.invalidatedAt());
            assertEquals(9007199254740993L, completed.hitCount());
            assertNull(completed.errorCode());
            assertEquals(TaskReadiness.State.READY, observed.readiness.state());
            assertEquals("RECOVERY_COMMITTED", observed.events.get(0));
            assertTrue(observed.events.stream().anyMatch(value -> value.equals("CLAIM:READY")));
            assertTrue(observed.events.stream().filter(value -> value.startsWith("SCAN:") || value.startsWith("CLAIM:"))
                    .allMatch(value -> value.endsWith(":READY")));
        } finally {
            observed.release.countDown();
            if (context != null) {
                context.close();
            }
            executor.shutdown();
            assertTrue(executor.awaitTermination(20, TimeUnit.SECONDS));
            gate = null;
        }
    }

    @Test
    void 恢复真commit后未知仍按任务自己的时间确认并保留两表不同时间() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("times");
            env.bean(TaskClaimService.class).claim(task).orElseThrow();
            env.sql("UPDATE documents SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-20 00:00:00+00' WHERE document_id='times'");
            env.sql("UPDATE embedding_tasks SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-21 00:00:00+00',attempt_count=7 WHERE task_id=?", task);
            var readiness = new TaskReadiness(env.bean(KnowledgeMapper.class));
            AtomicBoolean first = new AtomicBoolean(true);
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (first.compareAndSet(true, false)) {
                        throw new TransactionSystemException("仅测试：恢复实际commit后未知");
                    }
                }
            };
            var recovery = new TaskRecoveryService(env.bean(TaskRecoveryMapper.class), env.bean(KnowledgeMapper.class), readiness, manager);
            assertEquals(1, recovery.recover());
            assertEquals(TaskReadiness.State.READY, readiness.state());
            var row = env.bean(KnowledgeMapper.class).findTask(task);
            assertEquals("QUEUED", row.status());
            assertEquals(7, row.attemptCount());
            assertNull(row.runToken());
            assertNull(row.startedAt());
            assertEquals(Instant.parse("2026-10-20T00:00:00Z"), row.submittedAt());
            try (var connection = DriverManager.getConnection(env.context.getEnvironment().getProperty("spring.datasource.url"), "sa", "");
                 var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT submitted_at FROM embedding_tasks")) {
                assertTrue(result.next());
                assertEquals(Instant.parse("2026-10-21T00:00:00Z"), result.getObject(1, java.time.OffsetDateTime.class).toInstant());
            }
        }
    }

    @Test
    void 恢复SQL失败绝不READY并保留RUNNING现场() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("recover-failed");
            env.bean(TaskClaimService.class).claim(task).orElseThrow();
            TaskRecoveryMapper actual = env.bean(TaskRecoveryMapper.class);
            var broken = (TaskRecoveryMapper) Proxy.newProxyInstance(TaskRecoveryMapper.class.getClassLoader(),
                    new Class<?>[]{TaskRecoveryMapper.class}, (proxy, method, args) -> {
                        if (method.getName().equals("requeueRunning")) {
                            throw new DataAccessResourceFailureException("仅测试：恢复不可用");
                        }
                        try {
                            return method.invoke(actual, args);
                        } catch (InvocationTargetException exception) {
                            throw exception.getCause();
                        }
                    });
            var readiness = new TaskReadiness(env.bean(KnowledgeMapper.class));
            assertEquals(0, new TaskRecoveryService(broken, env.bean(KnowledgeMapper.class), readiness,
                    env.bean(PlatformTransactionManager.class)).recover());
            assertEquals(TaskReadiness.State.DEGRADED, readiness.state());
            assertFalse(readiness.accepting());
            assertEquals("RUNNING", env.bean(KnowledgeMapper.class).findTask(task).status());
        }
    }

    @Test
    void 真实生产READY及可达STOPPING的API和健康门禁一致() throws Exception {
        try (var context = start(false)) {
            assertTrue(context.getBean(dev.twelvetimes.vector.task.TaskExecutionHandler.class)
                    instanceof dev.twelvetimes.vector.task.ProductionTaskExecutionHandler);
            assertEquals(TaskReadiness.State.READY, context.getBean(TaskReadiness.class).state());
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
            assertEquals(200, get(client, port, "/api/documents").statusCode());
            assertEquals(200, get(client, port, "/health").statusCode());
            context.getBean(TaskReadiness.class).stopping();
            for (String path : List.of("/api/documents", "/api/tasks/00000000-0000-0000-0000-000000000001", "/health")) {
                assertEquals(503, get(client, port, path).statusCode());
            }
            var write = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/api/documents"))
                    .timeout(Duration.ofSeconds(5)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"documentId\":\"stopped\",\"text\":\"\"}")).build();
            assertEquals(503, client.send(write, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 关闭写许可必须等待已进入的真实短事务物理提交() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            var execution = env.bean(TaskClaimService.class).claim(env.submit("drain")).orElseThrow();
            CountDownLatch atCommit = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    atCommit.countDown();
                    try {
                        await(release);
                    } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    super.commit(status);
                }
            };
            var readiness = env.bean(TaskReadiness.class);
            var terminals = new TaskTerminalService(env.bean(TaskTerminalMapper.class), readiness, readiness.writeBarrier(), manager);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var writing = executor.submit(() -> terminals.succeed(execution, env.bean(dev.twelvetimes.vector.embedding.TextEmbedder.class).embed("ababa")));
                await(atCommit);
                var closing = executor.submit(() -> env.bean(TaskDispatcher.class).close());
                until(() -> !readiness.writeBarrier().isOpen());
                assertEquals(TaskReadiness.State.STOPPING, readiness.state());
                assertFalse(closing.isDone(), "屏障不能在已进入事务commit前返回");
                assertEquals("RUNNING", env.bean(KnowledgeMapper.class).findTask(execution.taskId()).status());
                release.countDown();
                assertEquals(TaskTerminalService.TerminalResult.APPLIED, writing.get(8, TimeUnit.SECONDS));
                closing.get(8, TimeUnit.SECONDS);
                assertEquals(TaskReadiness.State.STOPPED, readiness.state());
                assertEquals("SUCCEEDED", env.bean(KnowledgeMapper.class).findTask(execution.taskId()).status());
            } finally {
                release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(12, TimeUnit.SECONDS));
            }
        }
    }

    private ConfigurableApplicationContext start(boolean observer) {
        var args = new ArrayList<>(List.of("--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--server.port=0", "--server.address=127.0.0.1", "--APP_DATA_DIR=" + directory.toAbsolutePath(),
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--vector.scan-interval-ms=50"));
        if (observer) {
            args.add("--qa.recovery-observer=true");
        }
        return new SpringApplication(VectorSearchApplication.class, GateConfiguration.class).run(args.toArray(String[]::new));
    }

    private static HttpResponse<String> get(HttpClient client, int port, String path) throws Exception {
        return client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    static final class Gate {
        final CountDownLatch beforeCommit = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
        final AtomicBoolean first = new AtomicBoolean(true);
        final List<String> events = new CopyOnWriteArrayList<>();
        volatile TaskReadiness readiness;
        volatile DocumentAdmissionService admission;
        volatile TaskDispatcher dispatcher;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "qa.recovery-observer", havingValue = "true")
    static class GateConfiguration {
        @Bean
        static BeanPostProcessor observeRecovery() {
            Gate observed = gate;
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof TaskReadiness value) { observed.readiness = value; }
                    if (bean instanceof DocumentAdmissionService value) { observed.admission = value; }
                    if (bean instanceof TaskDispatcher value) { observed.dispatcher = value; }
                    if (bean instanceof PlatformTransactionManager manager) {
                        return new B2QaEnvironment.Manager(manager) {
                            @Override
                            public void commit(TransactionStatus status) {
                                if (observed.first.compareAndSet(true, false)) {
                                    observed.beforeCommit.countDown();
                                    try {
                                        await(observed.release);
                                    } catch (InterruptedException exception) {
                                        Thread.currentThread().interrupt();
                                        throw new AssertionError(exception);
                                    }
                                    super.commit(status);
                                    observed.events.add("RECOVERY_COMMITTED");
                                } else {
                                    super.commit(status);
                                }
                            }
                        };
                    }
                    if (bean instanceof KnowledgeMapper actual) {
                        return Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(), new Class<?>[]{KnowledgeMapper.class},
                                (proxy, method, args) -> {
                                    if (method.getName().equals("queued")) { observed.events.add("SCAN:" + observed.readiness.state()); }
                                    if (method.getName().equals("claim")) { observed.events.add("CLAIM:" + observed.readiness.state()); }
                                    try {
                                        return method.invoke(actual, args);
                                    } catch (InvocationTargetException exception) {
                                        throw exception.getCause();
                                    }
                                });
                    }
                    return bean;
                }
            };
        }
    }
}
