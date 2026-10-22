package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.health.BusinessReadiness;
import dev.twelvetimes.vector.health.DefaultBusinessReadiness;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultBusinessReadinessTest {
    @TempDir Path directory;

    @Test
    void 真实生产Web组成六路由数据库及统一算法全部满足才业务就绪() {
        try (var context = new SpringApplication(VectorSearchApplication.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:file:" + directory.toAbsolutePath() + "/production;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
                "--logging.level.root=ERROR", "--spring.main.banner-mode=off")) {
            var snapshot = context.getBean(BusinessReadiness.class).inspect();
            assertTrue(snapshot.businessReady());
            assertTrue(snapshot.taskReady());
            assertTrue(snapshot.routesReady());
            assertEquals(context.getBean(TextEmbedder.class).version(), snapshot.embeddingVersion());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countPending());
        }
    }

    @Test
    void 探针只读且不调用embed或改写任务状态() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            AtomicInteger borrowed = new AtomicInteger();
            DataSource observed = source(fixture.dataSource(), connection -> connection, borrowed);
            assertTrue(fixture.service(observed).inspect().businessReady());
            assertTrue(fixture.service(observed).inspect().businessReady());
            assertEquals(2, borrowed.get());
            assertEquals(0, fixture.database.getBean(KnowledgeMapper.class).countDocuments());
            assertEquals(0, fixture.database.getBean(KnowledgeMapper.class).countPending());
            assertEquals(TaskReadiness.State.READY, fixture.readiness.state());
        }
    }

    @Test
    void 恢复降级停止阶段绝不借数据库连接() throws Exception {
        try (var fixture = new Fixture(false, true)) {
            AtomicInteger borrowed = new AtomicInteger();
            DataSource forbidden = source(fixture.dataSource(), connection -> connection, borrowed);
            var probe = fixture.service(forbidden);
            assertEquals(TaskReadiness.State.RECOVERING, probe.inspect().taskState());
            fixture.readiness.block("测试降级");
            assertEquals(TaskReadiness.State.DEGRADED, probe.inspect().taskState());
            fixture.readiness.stopping();
            assertEquals(TaskReadiness.State.STOPPING, probe.inspect().taskState());
            fixture.readiness.stopped();
            var stopped = probe.inspect();
            assertEquals(TaskReadiness.State.STOPPED, stopped.taskState());
            assertFalse(stopped.businessReady());
            assertEquals(0, borrowed.get());
        }
    }

    @Test
    void JDBC连接或数据库元数据报告只读均不能就绪() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            for (boolean metadataReadOnly : new boolean[] {false, true}) {
                DataSource observed = source(fixture.dataSource(), actual -> connection(actual, (method, args, next) -> {
                    if (method.equals("isReadOnly")) { return !metadataReadOnly; }
                    if (method.equals("getMetaData")) {
                        DatabaseMetaData metadata = actual.getMetaData();
                        return Proxy.newProxyInstance(DatabaseMetaData.class.getClassLoader(), new Class<?>[] {DatabaseMetaData.class},
                                (proxy, target, arguments) -> target.getName().equals("isReadOnly") ? metadataReadOnly : invoke(metadata, target, arguments));
                    }
                    return next.call();
                }), new AtomicInteger());
                var snapshot = fixture.service(observed).inspect();
                assertTrue(snapshot.databaseReady());
                assertFalse(snapshot.databaseWritable());
                assertFalse(snapshot.taskReady());
                assertFalse(snapshot.businessReady());
                assertEquals(TaskReadiness.State.READY, fixture.readiness.state());
            }
        }
    }

    @Test
    void 缺少必需列或失效连接只返回失败快照不自动降级() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            DataSource invalid = source(fixture.dataSource(), actual -> connection(actual, (name, args, next) ->
                    name.equals("isValid") ? false : next.call()), new AtomicInteger());
            assertFalse(fixture.service(invalid).inspect().databaseReady());
            new JdbcTemplate(fixture.dataSource()).execute("ALTER TABLE embedding_tasks DROP COLUMN run_token");
            var missing = fixture.service(fixture.dataSource()).inspect();
            assertFalse(missing.databaseReady());
            assertFalse(missing.businessReady());
            assertEquals(TaskReadiness.State.READY, fixture.readiness.state());
        }
    }

    @Test
    void 缺少搜索Bean或任一路由不能报业务就绪() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            fixture.observed.getDefaultListableBeanFactory().destroySingleton("searchService");
            var missingSearch = fixture.service(fixture.dataSource()).inspect();
            assertTrue(missingSearch.taskReady());
            assertFalse(missingSearch.searchReady());
            assertFalse(missingSearch.businessReady());
            fixture.observed.getBeanFactory().registerSingleton("searchService", fixture.database.getBean(SearchService.class));
            fixture.mappings.unregisterMapping(fixture.routes.get(5));
            var missingRoute = fixture.service(fixture.dataSource()).inspect();
            assertTrue(missingRoute.taskReady());
            assertTrue(missingRoute.searchReady());
            assertFalse(missingRoute.routesReady());
            assertFalse(missingRoute.businessReady());
        }
    }

    @Test
    void 算法版本或统一实例不符不执行计算也不能就绪() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            TextEmbedder mismatched = new TextEmbedder() {
                @Override public String version() { return "wrong-version"; }
                @Override public float[] embed(String text) { throw new AssertionError("健康不允许计算向量"); }
            };
            fixture.observed.getDefaultListableBeanFactory().destroySingleton("embedder");
            fixture.observed.getBeanFactory().registerSingleton("embedder", mismatched);
            var wrong = new DefaultBusinessReadiness(fixture.dataSource(), fixture.readiness, fixture.dispatcher,
                    mismatched, fixture.properties, fixture.observed).inspect();
            assertFalse(wrong.algorithmReady());
            assertFalse(wrong.businessReady());
            fixture.observed.getBeanFactory().registerSingleton("anotherEmbedder", fixture.embedder);
            assertFalse(fixture.service(fixture.dataSource()).inspect().algorithmReady());
        }
    }

    @Test
    void 调度执行条件只读观察启动处理器和关闭状态() throws Exception {
        try (var withoutHandler = new Fixture(true, false)) {
            assertFalse(withoutHandler.dispatcher.executionAvailable());
            var snapshot = withoutHandler.service(withoutHandler.dataSource()).inspect();
            assertFalse(snapshot.dispatcherReady());
            assertFalse(snapshot.taskReady());
            assertFalse(snapshot.businessReady());
        }
        try (var active = new Fixture(true, true)) {
            assertTrue(active.dispatcher.executionAvailable());
            active.dispatcher.close();
            assertFalse(active.dispatcher.executionAvailable());
            assertFalse(active.service(active.dataSource()).inspect().businessReady());
        }
    }

    @Test
    void 数据库探针持屏障且停止交错后重取状态避免迟到健康成功() throws Exception {
        try (var fixture = new Fixture(true, true)) {
            CountDownLatch inConnection = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicInteger closed = new AtomicInteger();
            DataSource paused = source(fixture.dataSource(), actual -> connection(actual, (method, args, next) -> {
                if (method.equals("isValid")) { inConnection.countDown(); await(release); }
                if (method.equals("close")) { closed.incrementAndGet(); }
                return next.call();
            }), new AtomicInteger());
            var executor = Executors.newFixedThreadPool(2);
            try {
                var probe = executor.submit(() -> fixture.service(paused).inspect());
                assertTrue(inConnection.await(5, TimeUnit.SECONDS));
                fixture.readiness.stopping();
                var drain = executor.submit(fixture.readiness.writeBarrier()::closeAndDrain);
                TaskRecoveryServiceTest.until(() -> !fixture.readiness.writeBarrier().isOpen());
                assertFalse(drain.isDone(), "连接尚在健康短读取内，关闭屏障不能提前排空");
                release.countDown();
                var snapshot = probe.get(5, TimeUnit.SECONDS);
                drain.get(5, TimeUnit.SECONDS);
                assertEquals(1, closed.get());
                assertEquals(TaskReadiness.State.STOPPING, snapshot.taskState());
                assertFalse(snapshot.taskReady());
                assertFalse(snapshot.businessReady());
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    private final class Fixture implements AutoCloseable {
        final ConfigurableApplicationContext database;
        final GenericApplicationContext observed = new GenericApplicationContext();
        final TaskReadiness readiness;
        final TaskDispatcher dispatcher;
        final TextEmbedder embedder;
        final VectorProperties properties;
        final RequestMappingHandlerMapping mappings = new RequestMappingHandlerMapping();
        final List<RequestMappingInfo> routes = new ArrayList<>();

        Fixture(boolean ready, boolean handler) throws Exception {
            database = B2TestSupport.start(directory.resolve("fixture-" + System.nanoTime()));
            properties = database.getBean(VectorProperties.class);
            readiness = new TaskReadiness(database.getBean(KnowledgeMapper.class));
            if (ready) { readiness.ready(); }
            String version = database.getBean(TextEmbedder.class).version();
            embedder = new TextEmbedder() {
                @Override public String version() { return version; }
                @Override public float[] embed(String text) { throw new AssertionError("健康不允许计算向量"); }
            };
            KnowledgeMapper idle = (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                    new Class<?>[] {KnowledgeMapper.class}, (proxy, method, args) -> {
                        if (method.getName().equals("queued")) { return List.of(); }
                        throw new AssertionError("此调度夹具不领取或写入");
                    });
            dispatcher = new TaskDispatcher(idle, database.getBean(TaskClaimService.class), readiness, properties,
                    handler ? execution -> { throw new AssertionError("健康不应触发任务"); } : null);
            assertFalse(dispatcher.executionAvailable());
            dispatcher.start();
            observed.getBeanFactory().registerSingleton("embedder", embedder);
            observed.getBeanFactory().registerSingleton("searchService", database.getBean(SearchService.class));
            observed.getBeanFactory().registerSingleton("requestMappingHandlerMapping", mappings);
            observed.refresh();
            add(RequestMethod.POST, "/api/documents");
            add(RequestMethod.GET, "/api/tasks/{taskId}");
            add(RequestMethod.GET, "/api/documents");
            add(RequestMethod.GET, "/api/documents/{documentId}");
            add(RequestMethod.PUT, "/api/documents/{documentId}/invalidation");
            add(RequestMethod.POST, "/api/search");
        }

        private void add(RequestMethod method, String path) throws Exception {
            RequestMappingInfo info = RequestMappingInfo.paths(path).methods(method).build();
            routes.add(info);
            mappings.registerMapping(info, new DummyRoute(), DummyRoute.class.getMethod("handle"));
        }

        DataSource dataSource() { return database.getBean(DataSource.class); }
        DefaultBusinessReadiness service(DataSource source) {
            return new DefaultBusinessReadiness(source, readiness, dispatcher, embedder, properties, observed);
        }
        @Override public void close() { dispatcher.close(); observed.close(); database.close(); }
    }

    public static final class DummyRoute { public void handle() { } }
    @FunctionalInterface private interface Next { Object call() throws Throwable; }
    @FunctionalInterface private interface Interceptor { Object call(String name, Object[] args, Next next) throws Throwable; }
    @FunctionalInterface private interface Wrap { Connection apply(Connection actual) throws Exception; }

    private static DataSource source(DataSource actual, Wrap wrapper, AtomicInteger borrowed) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(), new Class<?>[] {DataSource.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) { borrowed.incrementAndGet(); return wrapper.apply((Connection) invoke(actual, method, args)); }
                    return invoke(actual, method, args);
                });
    }

    private static Connection connection(Connection actual, Interceptor interceptor) {
        return (Connection) Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[] {Connection.class},
                (proxy, method, args) -> interceptor.call(method.getName(), args, () -> invoke(actual, method, args)));
    }

    private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
        try { return method.invoke(target, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
