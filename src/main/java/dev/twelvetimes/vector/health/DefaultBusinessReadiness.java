package dev.twelvetimes.vector.health;

import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 聚合真实数据库、调度、算法和六路由的只读检查。不调用embed、不写业务数据，
 * 不在构造/启动时执行业务自测，也不因单次探测失败改写任务状态机。
 */
@Service
public final class DefaultBusinessReadiness implements BusinessReadiness {
    private static final List<Route> ROUTES = List.of(
            new Route(RequestMethod.POST, "/api/documents"),
            new Route(RequestMethod.GET, "/api/tasks/{taskId}"),
            new Route(RequestMethod.GET, "/api/documents"),
            new Route(RequestMethod.GET, "/api/documents/{documentId}"),
            new Route(RequestMethod.PUT, "/api/documents/{documentId}/invalidation"),
            new Route(RequestMethod.POST, "/api/search"));
    private static final String DOCUMENT_COLUMNS = """
            SELECT document_id,raw_text,channel,submitted_at,invalidated,invalidated_at,
            vector_bytes,embedding_version,vector_completed_at,hit_count FROM documents WHERE 1=0
            """;
    private static final String TASK_COLUMNS = """
            SELECT task_id,document_id,status,embedding_version,run_token,attempt_count,
            submitted_at,started_at,finished_at,error_code,error_message FROM embedding_tasks WHERE 1=0
            """;

    private final DataSource dataSource;
    private final TaskReadiness readiness;
    private final TaskDispatcher dispatcher;
    private final TextEmbedder embedder;
    private final VectorProperties properties;
    private final ApplicationContext context;

    public DefaultBusinessReadiness(DataSource dataSource, TaskReadiness readiness, TaskDispatcher dispatcher,
                                     TextEmbedder embedder, VectorProperties properties, ApplicationContext context) {
        this.dataSource = dataSource;
        this.readiness = readiness;
        this.dispatcher = dispatcher;
        this.embedder = embedder;
        this.properties = properties;
        this.context = context;
    }

    @Override
    public Snapshot inspect() {
        String version = version();
        boolean algorithmReady = algorithmReady(version);
        boolean searchReady = searchReady();
        boolean routesReady = routesReady();
        boolean databaseReady = false;
        boolean databaseWritable = false;
        // 不使用enterWrite抛出业务异常；健康在非READY时给失败快照，绝不能额外借连接。
        if (readiness.state() == TaskReadiness.State.READY && readiness.writeBarrier().isOpen()) {
            try (var lease = readiness.writeBarrier().enter()) {
                // 与关闭交错时，拿到许可后仍需重新检查状态，许可覆盖连接关闭。
                if (lease != null && readiness.state() == TaskReadiness.State.READY) {
                    try (Connection connection = dataSource.getConnection()) {
                        if (connection.isValid(2)) {
                            try (Statement statement = connection.createStatement()) {
                                statement.setQueryTimeout(2);
                                statement.executeQuery(DOCUMENT_COLUMNS).close();
                                statement.executeQuery(TASK_COLUMNS).close();
                            }
                            databaseReady = true;
                            databaseWritable = !connection.isReadOnly() && !connection.getMetaData().isReadOnly();
                        }
                    }
                }
            } catch (SQLException | RuntimeException ignored) {
                // 只返回布尔事实，不暴露SQL/路径/堆栈，也不借探针触发自动修复或恢复。
                databaseReady = false;
                databaseWritable = false;
            }
        }
        TaskReadiness.State latest = readiness.state();
        boolean dispatcherReady = dispatcher.executionAvailable();
        boolean taskReady = latest == TaskReadiness.State.READY && readiness.writeBarrier().isOpen()
                && databaseReady && databaseWritable && dispatcherReady && algorithmReady;
        return new Snapshot(latest, taskReady, databaseReady, databaseWritable, dispatcherReady,
                algorithmReady, searchReady, routesReady, version);
    }

    private String version() {
        try { return embedder.version(); }
        catch (RuntimeException ignored) { return null; }
    }

    private boolean algorithmReady(String version) {
        try {
            Map<String, TextEmbedder> instances = context.getBeansOfType(TextEmbedder.class);
            String expected = "char12-sha256-r" + properties.cpuRounds() + "-nfkc-root-ws-v1";
            return expected.equals(version) && instances.size() == 1 && instances.values().iterator().next() == embedder;
        } catch (RuntimeException ignored) { return false; }
    }

    private boolean searchReady() {
        try { return context.getBeanProvider(SearchService.class).getIfUnique() != null; }
        catch (RuntimeException ignored) { return false; }
    }

    private boolean routesReady() {
        try {
            RequestMappingHandlerMapping mapping = context.getBeanProvider(RequestMappingHandlerMapping.class).getIfUnique();
            if (mapping == null) { return false; }
            var registered = mapping.getHandlerMethods().keySet();
            return ROUTES.stream().allMatch(route -> registered.stream().anyMatch(info -> matches(info, route)));
        } catch (RuntimeException ignored) { return false; }
    }

    private static boolean matches(RequestMappingInfo mapping, Route route) {
        return mapping.getMethodsCondition().getMethods().contains(route.method())
                && mapping.getPatternValues().contains(route.path());
    }

    private record Route(RequestMethod method, String path) { }
}
