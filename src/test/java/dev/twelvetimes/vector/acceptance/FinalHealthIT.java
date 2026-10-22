package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.BootstrapHealthController;
import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.health.DefaultBusinessReadiness;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真实HTTP验证完整组成；组件故障注入仍驱动真实健康Service和Controller，不伪造Snapshot。 */
class FinalHealthIT {
    @TempDir Path directory;
    private static final AtomicInteger EMBEDS = new AtomicInteger();

    @Test
    void 真实生产健康重复探针全绿但不调用embed不写任务或计数() throws Exception {
        EMBEDS.set(0);
        try (var env = new B4QaEnvironment(directory, true, true, EmbedObserver.class, "--qa.final-embed-observer=true")) {
            env.realDocument("health", "ababa", null);
            env.bean(SearchService.class).search(new SearchRequest("ababa", 1, null));
            assertTrue(EMBEDS.get() > 0);
            EMBEDS.set(0);
            var before = env.bean(KnowledgeMapper.class).findDocument("health");
            for (int probe = 0; probe < 20; probe++) {
                var response = get(env);
                assertEquals(200, response.statusCode());
                var body = env.bean(ObjectMapper.class).readTree(response.body());
                assertEquals("UP", body.get("status").asString());
                assertEquals("B4", body.get("stage").asString());
                assertEquals("READY", body.get("taskState").asString());
                assertTrue(body.get("businessReady").asBoolean());
                assertTrue(body.get("taskReady").asBoolean());
                assertEquals("char12-sha256-r32-nfkc-root-ws-v1", body.get("embeddingVersion").asString());
                for (String check : java.util.List.of("databaseReady", "databaseWritable", "dispatcherReady", "algorithmReady", "searchReady", "routesReady")) {
                    assertTrue(body.get("checks").get(check).asBoolean(), check);
                }
            }
            assertEquals(0, EMBEDS.get());
            assertEquals(before, env.bean(KnowledgeMapper.class).findDocument("health"));
            assertEquals(1, env.bean(KnowledgeMapper.class).countDocuments());
            try (var connection = env.bean(DataSource.class).getConnection(); var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT COUNT(*) FROM embedding_tasks")) {
                assertTrue(rows.next());
                assertEquals(1, rows.getLong(1));
            }
            B4Evidence.record("FINAL_HEALTH", "probes=20;HTTP=200;businessReady=true;embedCalls=0;documents=1;tasks=1;hitCount=1");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"RECOVERING", "DEGRADED", "STOPPING", "STOPPED"})
    void 非READY状态真实探针及控制器503且完全不借数据库连接(String state) {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            var readiness = new TaskReadiness(env.bean(KnowledgeMapper.class));
            if (state.equals("DEGRADED")) { readiness.block("仅测试：降级"); }
            if (state.equals("STOPPING") || state.equals("STOPPED")) { readiness.stopping(); }
            if (state.equals("STOPPED")) { readiness.stopped(); }
            AtomicInteger borrowed = new AtomicInteger();
            DataSource forbidden = proxy(DataSource.class, (method, args) -> {
                if (method.getName().equals("getConnection")) { borrowed.incrementAndGet(); throw new AssertionError("非READY不准借连接"); }
                return invoke(env.bean(DataSource.class), method, args);
            });
            var result = controller(env, forbidden, readiness, env.bean(TextEmbedder.class), env.context).health();
            assertEquals(503, result.getStatusCode().value());
            assertEquals(false, result.getBody().get("businessReady"));
            assertEquals(state, result.getBody().get("taskState"));
            assertEquals(0, borrowed.get());
            assertEquals(TaskReadiness.State.valueOf(state), readiness.state(), "健康不能自行修复或改写任务状态");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"连接不可用", "连接只读", "元数据只读", "字段不可读"})
    void READY数据库任一异常或只读都不能报业务就绪(String mode) {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            AtomicInteger borrowed = new AtomicInteger();
            DataSource modified = proxy(DataSource.class, (method, args) -> {
                if (!method.getName().equals("getConnection")) { return invoke(env.bean(DataSource.class), method, args); }
                borrowed.incrementAndGet();
                if (mode.equals("连接不可用")) { throw new SQLException("secret-path SQL unavailable"); }
                Connection actual = (Connection) invoke(env.bean(DataSource.class), method, args);
                return proxy(Connection.class, (connectionMethod, connectionArgs) -> {
                    if (connectionMethod.getName().equals("commit") || connectionMethod.getName().equals("rollback")) {
                        throw new AssertionError("健康不应提交或回滚业务写事务");
                    }
                    if (connectionMethod.getName().equals("isReadOnly") && mode.equals("连接只读")) { return true; }
                    if (connectionMethod.getName().equals("getMetaData") && mode.equals("元数据只读")) {
                        DatabaseMetaData metadata = actual.getMetaData();
                        return proxy(DatabaseMetaData.class, (metadataMethod, metadataArgs) -> metadataMethod.getName().equals("isReadOnly")
                                ? true : invoke(metadata, metadataMethod, metadataArgs));
                    }
                    if (connectionMethod.getName().equals("createStatement")) {
                        Statement statement = (Statement) invoke(actual, connectionMethod, connectionArgs);
                        return proxy(Statement.class, (statementMethod, statementArgs) -> {
                            if (statementMethod.getName().startsWith("execute")) {
                                assertEquals("executeQuery", statementMethod.getName());
                                assertTrue(((String) statementArgs[0]).stripLeading().startsWith("SELECT"));
                                if (mode.equals("字段不可读")) { throw new SQLException("secret-field unavailable"); }
                            }
                            return invoke(statement, statementMethod, statementArgs);
                        });
                    }
                    return invoke(actual, connectionMethod, connectionArgs);
                });
            });
            TaskReadiness readiness = env.bean(TaskReadiness.class);
            var result = controller(env, modified, readiness, env.bean(TextEmbedder.class), env.context).health();
            assertEquals(503, result.getStatusCode().value());
            assertEquals(false, result.getBody().get("businessReady"));
            assertEquals(false, ((java.util.Map<?, ?>) result.getBody().get("checks")).get("databaseWritable"));
            assertFalse(result.getBody().toString().contains("secret"));
            assertEquals(1, borrowed.get());
            assertEquals(TaskReadiness.State.READY, readiness.state());
        }
    }

    @Test
    void 明确B2隔离组成缺生产处理器健康503但基础数据库检查仍真() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            var response = get(env);
            assertEquals(503, response.statusCode());
            var body = env.bean(ObjectMapper.class).readTree(response.body());
            assertFalse(body.get("businessReady").asBoolean());
            assertTrue(body.get("checks").get("databaseReady").asBoolean());
            assertFalse(body.get("checks").get("dispatcherReady").asBoolean());
        }
    }

    @Test
    void 实际调度停止后生产HTTP健康503() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            env.bean(TaskDispatcher.class).close();
            assertEquals(503, get(env).statusCode());
        }
    }

    @Test
    void 缺少搜索路由的真实生产HTTP健康不能全绿() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            var mapping = env.bean(RequestMappingHandlerMapping.class);
            var route = mapping.getHandlerMethods().keySet().stream().filter(info -> info.getPatternValues().contains("/api/search")
                    && info.getMethodsCondition().getMethods().contains(RequestMethod.POST)).findFirst().orElseThrow();
            mapping.unregisterMapping(route);
            var response = get(env);
            assertEquals(503, response.statusCode());
            var checks = env.bean(ObjectMapper.class).readTree(response.body()).get("checks");
            assertFalse(checks.get("routesReady").asBoolean());
            assertTrue(checks.get("databaseReady").asBoolean());
            assertTrue(checks.get("searchReady").asBoolean());
        }
    }

    @Test
    void 算法实例不一致或搜索组件缺失均503() {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            TextEmbedder wrong = new TextEmbedder() {
                @Override public float[] embed(String text) { throw new AssertionError("探针不能embed"); }
                @Override public String version() { return "wrong-version"; }
            };
            var algorithm = controller(env, env.bean(DataSource.class), env.bean(TaskReadiness.class), wrong, env.context).health();
            assertEquals(503, algorithm.getStatusCode().value());
            assertEquals(false, ((java.util.Map<?, ?>) algorithm.getBody().get("checks")).get("algorithmReady"));
            ApplicationContext withoutSearch = proxy(ApplicationContext.class, (method, args) -> {
                if (method.getName().equals("getBeanProvider") && args[0] == SearchService.class) {
                    return new StaticListableBeanFactory().getBeanProvider(SearchService.class);
                }
                return invoke(env.context, method, args);
            });
            var search = controller(env, env.bean(DataSource.class), env.bean(TaskReadiness.class), env.bean(TextEmbedder.class), withoutSearch).health();
            assertEquals(503, search.getStatusCode().value());
            assertEquals(false, ((java.util.Map<?, ?>) search.getBody().get("checks")).get("searchReady"));
        }
    }

    private static BootstrapHealthController controller(B4QaEnvironment env, DataSource source, TaskReadiness readiness,
                                                         TextEmbedder embedder, ApplicationContext context) {
        return new BootstrapHealthController(new DefaultBusinessReadiness(source, readiness, env.bean(TaskDispatcher.class),
                embedder, env.bean(VectorProperties.class), context));
    }

    private static HttpResponse<String> get(B4QaEnvironment env) throws Exception {
        return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build().send(
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + "/health"))
                        .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }

    @FunctionalInterface
    interface Call { Object invoke(Method method, Object[] args) throws Throwable; }

    private static <T> T proxy(Class<T> type, Call call) {
        return type.cast(Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, (ignored, method, args) -> call.invoke(method, args)));
    }

    private static Object invoke(Object actual, Method method, Object[] args) throws Throwable {
        try { return method.invoke(actual, args); }
        catch (InvocationTargetException exception) { throw exception.getCause(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "qa.final-embed-observer", havingValue = "true")
    static class EmbedObserver {
        @Bean
        static BeanPostProcessor observeEmbed() {
            return new BeanPostProcessor() {
                @Override public Object postProcessAfterInitialization(Object bean, String name) {
                    if (!(bean instanceof TextEmbedder original)) { return bean; }
                    return new TextEmbedder() {
                        @Override public float[] embed(String text) { EMBEDS.incrementAndGet(); return original.embed(text); }
                        @Override public String version() { return original.version(); }
                    };
                }
            };
        }
    }
}
