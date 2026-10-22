package dev.twelvetimes.vector.acceptance;

import com.zaxxer.hikari.HikariDataSource;
import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.TaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.HexFormat;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionDefinition;

/** 独立JVM故障探针：只在测试组成装饰生产组件，生产handler和B1始终真实。 */
public final class B3ProcessProbe {
    private static String mode;
    private static String url;
    private static final CountDownLatch CPU_RELEASE = new CountDownLatch(1);
    private static final CountDownLatch HANDLER_RETURNED = new CountDownLatch(1);
    private static final AtomicBoolean CHECKPOINT = new AtomicBoolean();
    private static final AtomicBoolean DATASOURCE_CLOSED = new AtomicBoolean();
    private static final AtomicInteger LATE_SQL = new AtomicInteger();

    private B3ProcessProbe() {
    }

    public static void main(String[] args) throws Exception {
        mode = args[0];
        url = "jdbc:h2:file:" + Path.of(args[1]).toAbsolutePath() + "/knowledge;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE";
        var app = new SpringApplication(VectorSearchApplication.class, Hooks.class);
        ConfigurableApplicationContext context = app.run("--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--server.address=127.0.0.1", "--server.port=0", "--APP_DATA_DIR=" + Path.of(args[1]).toAbsolutePath(),
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--qa.process-probe=true", "--vector.scan-interval-ms=50",
                "--vector.shutdown-wait-seconds=" + (mode.equals("SIGTERM_TIMEOUT") ? 1 : 8));
        event("APP_READY", "port=" + ((WebServerApplicationContext) context).getWebServer().getPort()
                + ";state=" + context.getBean(TaskReadiness.class).state());
        Thread commands = new Thread(() -> {
            try (var input = new BufferedReader(new InputStreamReader(System.in))) {
                String command;
                while ((command = input.readLine()) != null) {
                    if (command.equals("RELEASE")) {
                        CPU_RELEASE.countDown();
                    }
                }
            } catch (Exception exception) {
                event("COMMAND_ERROR", exception.getClass().getSimpleName());
            }
        }, "qa-probe-commands");
        commands.setDaemon(true);
        commands.start();
        HikariDataSource dataSource = context.getBean(HikariDataSource.class);
        if (!dataSource.getJdbcUrl().contains("DB_CLOSE_ON_EXIT=FALSE")) {
            throw new AssertionError("生产默认URL未关闭H2独立JVM关闭hook");
        }
        event("PRODUCTION_URL", dataSource.getJdbcUrl());
        TaskReadiness readiness = context.getBean(TaskReadiness.class);
        Runtime.getRuntime().addShutdownHook(new Thread(() -> shutdownEvidence(context, dataSource, readiness), "qa-shutdown-evidence"));
        if (mode.equals("RESTART")) {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20);
            while (System.nanoTime() < deadline) {
                String snapshot = snapshot();
                if (snapshot.contains("status=SUCCEEDED")) {
                    event("RESTART_SUCCEEDED", snapshot);
                    new CountDownLatch(1).await(90, TimeUnit.SECONDS);
                    return;
                }
                Thread.yield();
            }
            throw new AssertionError("真实重启未在期限内成功");
        }
        context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("crash", "ababa", null));
        new CountDownLatch(1).await(90, TimeUnit.SECONDS);
    }

    private static void shutdownEvidence(ConfigurableApplicationContext context, HikariDataSource dataSource, TaskReadiness readiness) {
        try {
            Thread stateWatcher = new Thread(() -> {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
                while (System.nanoTime() < deadline) {
                    if (readiness.state() == TaskReadiness.State.STOPPING) {
                        event("STOPPING", "生产生命周期已拒接");
                        return;
                    }
                    Thread.yield();
                }
            }, "qa-stopping-observer");
            stateWatcher.setDaemon(true);
            stateWatcher.start();
            // 与Spring自己的hook并行关闭同一上下文，真实close返回后才记录数据源状态。
            context.close();
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            while (!dataSource.isClosed() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            if (!dataSource.isClosed()) {
                throw new AssertionError("真实数据源未关闭");
            }
            DATASOURCE_CLOSED.set(true);
            event("DATASOURCE_CLOSED", "closed=true");
            if (mode.equals("SIGTERM_TIMEOUT")) {
                // 故意在真实数据源关闭后释放CPU，以检测迟到回写，不能以进程直接退出回避。
                CPU_RELEASE.countDown();
                if (!HANDLER_RETURNED.await(10, TimeUnit.SECONDS)) {
                    throw new AssertionError("迟到CPU未返回");
                }
                event("LATE_SQL_COUNT", "count=" + LATE_SQL.get());
            }
            event("SHUTDOWN_SNAPSHOT", snapshot());
        } catch (Throwable exception) {
            event("HOOK_FAILURE", exception.getClass().getSimpleName() + ":" + exception.getMessage());
        }
    }

    /** 所有检查点在真实commit返回或指定未提交SQL之后发出；事件不等同于任意日志字符串。 */
    private static void checkpoint(String stage) throws Exception {
        if (CHECKPOINT.compareAndSet(false, true)) {
            event(stage, snapshot());
            if (!new CountDownLatch(1).await(90, TimeUnit.SECONDS)) {
                throw new AssertionError("父进程未按检查点强杀探针");
            }
        }
    }

    static String snapshot() throws Exception {
        try (var connection = DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT t.status,t.run_token,t.attempt_count,t.finished_at,"
                     + "d.vector_bytes,d.vector_completed_at,t.task_id,d.hit_count,d.invalidated "
                     + "FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id WHERE d.document_id='crash'")) {
            if (!row.next()) {
                return "status=ABSENT";
            }
            byte[] bytes = row.getBytes(5);
            return "status=" + row.getString(1) + ";token=" + row.getString(2) + ";attempt=" + row.getInt(3)
                    + ";finished=" + row.getString(4) + ";sha=" + (bytes == null ? "null"
                    : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)))
                    + ";vectorTime=" + row.getString(6) + ";task=" + row.getString(7)
                    + ";hitCount=" + row.getLong(8) + ";invalidated=" + row.getBoolean(9);
        }
    }

    private static synchronized void event(String name, String detail) {
        System.out.println("QA_EVENT|" + ProcessHandle.current().pid() + "|" + name + "|" + detail);
        System.out.flush();
    }

    private static Object invoke(Object actual, Method method, Object[] args) throws Throwable {
        try {
            return method.invoke(actual, args);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "qa.process-probe", havingValue = "true")
    static class Hooks {
        @Bean
        static BeanPostProcessor qaProcessHooks() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof HikariDataSource dataSource) {
                        // 只读实际生产默认URL供快照使用，探针不再覆盖spring.datasource.url。
                        url = dataSource.getJdbcUrl();
                    }
                    if (bean instanceof PlatformTransactionManager manager && !mode.equals("RESTART")) {
                        return new B2QaEnvironment.Manager(manager) {
                            @Override
                            public TransactionStatus getTransaction(TransactionDefinition definition) {
                                try {
                                    return super.getTransaction(definition);
                                } catch (RuntimeException exception) {
                                    Throwable cause = exception;
                                    while (cause.getCause() != null) { cause = cause.getCause(); }
                                    event("TX_FAILURE", exception + ";root=" + cause + ";SQLState="
                                            + (cause instanceof java.sql.SQLException sql ? sql.getSQLState() : "none"));
                                    throw exception;
                                }
                            }
                            @Override
                            public void commit(TransactionStatus status) {
                                super.commit(status);
                                try {
                                    String persisted = snapshot();
                                    if (mode.equals("QUEUED") && persisted.contains("status=QUEUED")) {
                                        checkpoint("QUEUED_COMMITTED");
                                    } else if (mode.equals("RUNNING") && persisted.contains("status=RUNNING")) {
                                        checkpoint("RUNNING_COMMITTED");
                                    } else if (mode.equals("TERMINAL_COMMITTED") && persisted.contains("status=SUCCEEDED")) {
                                        checkpoint("TERMINAL_COMMITTED");
                                    }
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                            }
                        };
                    }
                    if (bean instanceof KnowledgeMapper || bean instanceof TaskTerminalMapper) {
                        Class<?> type = bean instanceof KnowledgeMapper ? KnowledgeMapper.class : TaskTerminalMapper.class;
                        return Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (proxy, method, args) -> {
                            if (mode.equals("QUEUED") && method.getName().equals("queued")) {
                                return List.of();
                            }
                            if (DATASOURCE_CLOSED.get()) {
                                LATE_SQL.incrementAndGet();
                            }
                            Object result;
                            try {
                                result = invoke(bean, method, args);
                            } catch (Throwable exception) {
                                event("SQL_FAILURE", method.getName() + ":" + exception);
                                throw exception;
                            }
                            if (mode.equals("TERMINAL_BEFORE_COMMIT") && method.getName().equals("writeVector")) {
                                checkpoint("TERMINAL_BEFORE_COMMIT");
                            }
                            return result;
                        });
                    }
                    if (bean instanceof TextEmbedder embedder && mode.startsWith("SIGTERM")) {
                        return new TextEmbedder() {
                            @Override
                            public float[] embed(String text) {
                                float[] vector = embedder.embed(text);
                                try {
                                    event("CPU_READY", "真实B1已计算;sha=" + HexFormat.of().formatHex(
                                            MessageDigest.getInstance("SHA-256").digest(
                                                    dev.twelvetimes.vector.embedding.VectorCodec.encode(vector))));
                                } catch (Exception exception) {
                                    throw new AssertionError(exception);
                                }
                                try {
                                    if (!CPU_RELEASE.await(45, TimeUnit.SECONDS)) {
                                        throw new AssertionError("CPU释放超时");
                                    }
                                } catch (InterruptedException exception) {
                                    Thread.currentThread().interrupt();
                                    throw new AssertionError(exception);
                                }
                                return vector;
                            }
                            @Override
                            public String version() { return embedder.version(); }
                        };
                    }
                    if (bean instanceof TaskExecutionHandler handler && mode.startsWith("SIGTERM")) {
                        return (TaskExecutionHandler) execution -> {
                            try {
                                handler.handle(execution);
                            } finally {
                                HANDLER_RETURNED.countDown();
                                event("HANDLER_RETURNED", "生产处理器已返回");
                            }
                        };
                    }
                    return bean;
                }
            };
        }
    }
}
