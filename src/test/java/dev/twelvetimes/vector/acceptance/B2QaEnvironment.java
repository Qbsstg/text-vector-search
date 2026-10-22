package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 后段验收专用环境；每次完整启动生产应用，文件库与HTTP端口均隔离。 */
final class B2QaEnvironment implements AutoCloseable {
    final ConfigurableApplicationContext context;

    B2QaEnvironment(Path directory, boolean web, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=" + (web ? "servlet" : "none"),
                "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--server.address=127.0.0.1",
                "--server.port=0", "--spring.datasource.url=jdbc:h2:file:" + directory.toAbsolutePath()
                        + "/knowledge;WRITE_DELAY=0", "--spring.datasource.username=sa", "--spring.datasource.password="));
        args.addAll(List.of(overrides));
        context = new SpringApplication(B2CompositionTestApplication.class).run(args.toArray(String[]::new));
    }

    <T> T bean(Class<T> type) {
        return context.getBean(type);
    }

    int port() {
        return ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    String submit(String id) {
        return bean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest(id, "测试原文", null)).taskId();
    }

    void sql(String sql, Object... args) throws Exception {
        try (var connection = bean(DataSource.class).getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) {
                statement.setObject(index + 1, args[index]);
            }
            statement.executeUpdate();
        }
    }

    @FunctionalInterface
    interface Invocation {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface Interceptor {
        Object invoke(String name, Object[] args, Invocation proceed) throws Throwable;
    }

    KnowledgeMapper mapper(Interceptor interceptor) {
        KnowledgeMapper actual = bean(KnowledgeMapper.class);
        return (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                new Class<?>[]{KnowledgeMapper.class}, (proxy, method, args) -> interceptor.invoke(method.getName(), args, () -> {
                    try {
                        return method.invoke(actual, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
    }

    /** 仅等待可观察状态，不用固定sleep猜测扫描或Worker已经完成。 */
    static void until(BooleanSupplier predicate) {
        long deadline = System.nanoTime() + Duration.ofSeconds(8).toNanos();
        while (System.nanoTime() < deadline) {
            if (predicate.getAsBoolean()) {
                return;
            }
            Thread.yield();
        }
        fail("等待受控状态超时");
    }

    static void await(java.util.concurrent.CountDownLatch latch) throws InterruptedException {
        assertTrue(latch.await(8, java.util.concurrent.TimeUnit.SECONDS));
    }

    @Override
    public void close() {
        context.close();
    }

    /** 保留真实事务管理器，故障仅在装饰器指定的提交边界注入。 */
    static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager actual;

        Manager(PlatformTransactionManager actual) {
            this.actual = actual;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return actual.getTransaction(definition);
        }

        @Override
        public void commit(TransactionStatus status) {
            actual.commit(status);
        }

        @Override
        public void rollback(TransactionStatus status) {
            actual.rollback(status);
        }
    }
}
