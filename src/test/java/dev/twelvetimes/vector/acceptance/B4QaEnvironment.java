package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.SearchCandidateSource;
import dev.twelvetimes.vector.search.SearchHitCounter;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;

/** 正常闭环采用生产组成；人工状态夹具采用明确B2组成，避免生产调度与测试造数竞争。 */
final class B4QaEnvironment implements AutoCloseable {
    final ConfigurableApplicationContext context;

    B4QaEnvironment(Path directory, boolean production, boolean web, Class<?> extra, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=" + (web ? "servlet" : "none"),
                "--spring.main.banner-mode=off", "--logging.level.root=WARN", "--server.port=0",
                "--server.address=127.0.0.1", "--APP_DATA_DIR=" + directory.toAbsolutePath(),
                "--spring.datasource.username=sa", "--spring.datasource.password=", "--vector.scan-interval-ms=50"));
        args.addAll(List.of(overrides));
        Class<?> source = production ? VectorSearchApplication.class : B2CompositionTestApplication.class;
        context = new SpringApplication(extra == null ? new Class<?>[]{source} : new Class<?>[]{source, extra})
                .run(args.toArray(String[]::new));
    }

    <T> T bean(Class<T> type) { return context.getBean(type); }

    int port() { return ((WebServerApplicationContext) context).getWebServer().getPort(); }

    void realDocument(String id, String text, String channel) {
        String task = bean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest(id, text, channel)).taskId();
        until(() -> "SUCCEEDED".equals(bean(KnowledgeMapper.class).findTask(task).status()));
    }

    /** 一笔真实JDBC事务构造数学/过滤夹具；不将这些人工状态标作生产B1执行结果。 */
    void fixture(String id, String text, String channel, float[] vector, String status) throws Exception {
        String version = bean(TextEmbedder.class).version();
        try (var connection = bean(DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try (var doc = connection.prepareStatement("INSERT INTO documents(document_id,raw_text,channel,submitted_at,"
                    + "vector_bytes,embedding_version,vector_completed_at) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,?)");
                 var task = connection.prepareStatement("INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at)"
                         + " VALUES(?,?,?,?,CURRENT_TIMESTAMP)")) {
                doc.setString(1, id);
                doc.setString(2, text);
                doc.setString(3, channel);
                doc.setBytes(4, vector == null ? null : VectorCodec.encode(vector));
                doc.setString(5, vector == null ? null : version);
                doc.setObject(6, vector == null ? null : Timestamp.from(Instant.now()));
                doc.executeUpdate();
                task.setString(1, UUID.randomUUID().toString());
                task.setString(2, id);
                task.setString(3, status);
                task.setString(4, version);
                task.executeUpdate();
                connection.commit();
            }
        }
    }

    void sql(String sql, Object... args) throws Exception {
        try (var connection = bean(DataSource.class).getConnection(); var statement = connection.prepareStatement(sql)) {
            for (int index = 0; index < args.length; index++) { statement.setObject(index + 1, args[index]); }
            statement.executeUpdate();
        }
    }

    long hits(String id) { return bean(KnowledgeMapper.class).findDocument(id).hitCount(); }

    VectorProperties properties(int permits, int maxDocuments) {
        var p = bean(VectorProperties.class);
        return new VectorProperties(p.workerCount(), p.queueCapacity(), p.maxPendingTasks(), maxDocuments, permits,
                p.maxTextCodePoints(), p.maxRequestBytes(), p.maxTopK(), p.maxPageSize(), p.cpuRounds(),
                p.scanIntervalMs(), p.scanBatchSize(), p.shutdownWaitSeconds());
    }

    SearchService service(TextEmbedder embedder, SearchCandidateSource source, SearchHitCounter counter, int permits) {
        return new SearchService(bean(RequestValidator.class), embedder, source, counter, bean(TaskReadiness.class),
                properties(permits, bean(VectorProperties.class).maxDocuments()));
    }

    SearchMapper mapper(B2QaEnvironment.Interceptor interceptor) {
        SearchMapper actual = bean(SearchMapper.class);
        return (SearchMapper) Proxy.newProxyInstance(SearchMapper.class.getClassLoader(), new Class<?>[]{SearchMapper.class},
                (proxy, method, args) -> interceptor.invoke(method.getName(), args, () -> {
                    try { return method.invoke(actual, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                }));
    }

    @Override
    public void close() { context.close(); }
}
