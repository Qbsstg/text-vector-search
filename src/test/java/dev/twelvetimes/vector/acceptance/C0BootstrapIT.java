package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.api.contract.ApiModels;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.config.VectorProperties;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.context.properties.bind.validation.BindValidationException;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

/** 仅验证C0基础设施，不据此宣称业务接口或任务恢复已完成。 */
class C0BootstrapIT {
    @TempDir Path directory;

    @Test
    void realHttpHealthDefaultsAndCompleteApplicationRestartPreserveBothTables() throws Exception {
        Path db = directory.resolve("restart/knowledge");
        try (var context = start(db, true)) {
            assertDefaults(context.getBean(VectorProperties.class));
            assertHealth(context);
            try (var connection = context.getBean(DataSource.class).getConnection()) {
                assertTrue(connection.getMetaData().getURL().startsWith("jdbc:h2:file:" + db));
                connection.setAutoCommit(false);
                try (var statement = connection.createStatement()) {
                    statement.executeUpdate("INSERT INTO documents(document_id,raw_text,channel,submitted_at,hit_count) "
                            + "VALUES ('C0-sentinel','retained text','default',CURRENT_TIMESTAMP,9007199254740993)");
                    statement.executeUpdate("INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at) "
                            + "VALUES ('00000000-0000-0000-0000-000000000001','C0-sentinel','QUEUED','c0-qa',CURRENT_TIMESTAMP)");
                    connection.commit();
                }
            }
        }
        assertTrue(Files.exists(Path.of(db + ".mv.db")), "Must use a persistent H2 file");
        // 先完整关闭第一个Spring上下文及连接池，再重新打开同一数据库文件。
        try (var restarted = start(db, true)) {
            assertHealth(restarted);
            try (Connection connection = restarted.getBean(DataSource.class).getConnection();
                 var statement = connection.createStatement();
                 var rows = statement.executeQuery("SELECT d.raw_text,d.hit_count,t.status,t.embedding_version "
                         + "FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id "
                         + "WHERE d.document_id='C0-sentinel'")) {
                assertTrue(rows.next());
                assertEquals("retained text", rows.getString(1));
                assertEquals(9007199254740993L, rows.getLong(2));
                assertEquals("QUEUED", rows.getString(3));
                assertEquals("c0-qa", rows.getString(4));
                assertFalse(rows.next());
            }
        }
    }

    @Test
    void productionConfigurationBindsOverrides() {
        try (var context = start(directory.resolve("overrides/knowledge"), false,
                "--vector.worker-count=2", "--vector.queue-capacity=7", "--vector.max-pending-tasks=23",
                "--vector.max-documents=41", "--vector.search-permits=3", "--vector.max-text-code-points=99",
                "--vector.max-request-bytes=4096", "--vector.max-top-k=9", "--vector.max-page-size=11",
                "--vector.cpu-rounds=5", "--vector.scan-interval-ms=75", "--vector.scan-batch-size=4",
                "--vector.shutdown-wait-seconds=2")) {
            assertEquals(new VectorProperties(2, 7, 23, 41, 3, 99, 4096, 9, 11, 5, 75, 4, 2),
                    context.getBean(VectorProperties.class));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"--vector.queue-capacity=0", "--vector.worker-count=17",
            "--vector.max-text-code-points=20001"})
    void invalidProductionConfigurationPreventsStartup(String invalidOverride) {
        RuntimeException failure = assertThrows(RuntimeException.class, () -> {
            try (var ignored = start(directory.resolve("invalid/knowledge"), false, invalidOverride)) {
                fail("Invalid production configuration must not start");
            }
        });
        Throwable cause = failure;
        while (cause != null && !(cause instanceof BindValidationException)) {
            cause = cause.getCause();
        }
        assertInstanceOf(BindValidationException.class, cause,
                "Failure must be configuration validation, not an unrelated startup error");
    }

    @Test
    void productionJsonMapperPreservesFrozenDtoWireTypesAndNullableFields() throws Exception {
        try (var context = start(directory.resolve("dto/knowledge"), false)) {
            ObjectMapper mapper = context.getBean(ObjectMapper.class);
            Instant submitted = Instant.parse("2026-10-22T01:02:03Z");
            var detail = new ApiModels.DocumentDetail("9007199254740993", "default", "task-1",
                    TaskStatus.SUCCEEDED, true, true, submitted, submitted, submitted,
                    "9007199254740993", "<script>text only</script>");
            var json = mapper.readTree(mapper.writeValueAsString(detail));
            assertEquals("9007199254740993", json.get("documentId").asString());
            assertTrue(json.get("hitCount").isString());
            assertEquals("9007199254740993", json.get("hitCount").asString());
            assertEquals("2026-10-22T01:02:03Z", json.get("submittedAt").asString());
            assertEquals("SUCCEEDED", json.get("taskStatus").asString());
            assertTrue(json.get("vectorReady").asBoolean());
            assertTrue(json.get("invalidated").asBoolean());
            assertFalse(json.has("vectorBytes"));
            assertEquals(detail, mapper.readValue(mapper.writeValueAsString(detail), ApiModels.DocumentDetail.class));
            var task = mapper.readTree(mapper.writeValueAsString(new ApiModels.TaskView("task-1", "doc-1",
                    TaskStatus.QUEUED, submitted, null, null, null, null, null)));
            for (String field : List.of("startedAt", "finishedAt", "errorCode", "errorMessage", "document")) {
                assertTrue(task.has(field), field + " must be present");
                assertTrue(task.get(field).isNull(), field + " must be JSON null");
            }
            assertEquals("", mapper.readValue("{\"documentId\":\"doc-1\",\"text\":\"\"}",
                    ApiModels.SubmitDocumentRequest.class).text());
            assertNull(mapper.readValue("{\"documentId\":\"doc-1\",\"text\":null}",
                    ApiModels.SubmitDocumentRequest.class).text());
        }
    }

    private ConfigurableApplicationContext start(Path db, boolean web, String... overrides) {
        var arguments = new ArrayList<>(List.of("--server.port=0", "--server.address=127.0.0.1",
                "--spring.main.web-application-type=" + (web ? "servlet" : "none"),
                "--spring.datasource.url=jdbc:h2:file:" + db.toAbsolutePath() + ";WRITE_DELAY=0",
                "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--logging.level.root=WARN", "--spring.main.banner-mode=off"));
        arguments.addAll(List.of(overrides));
        return new SpringApplication(B2CompositionTestApplication.class).run(arguments.toArray(String[]::new));
    }

    private void assertHealth(ConfigurableApplicationContext context) throws Exception {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        assertTrue(port > 0);
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/health"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        var response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        var json = context.getBean(ObjectMapper.class).readTree(response.body());
        assertEquals("UP", json.get("status").asString());
        // 阶段元数据随真实健康控制器升级；该隔离组成仍仅测基础设施，不声称完整业务就绪。
        assertEquals("B3", json.get("stage").asString());
        assertEquals("READY", json.get("taskState").asString());
        assertTrue(json.get("taskReady").asBoolean());
        assertFalse(json.get("businessReady").asBoolean());
    }

    private void assertDefaults(VectorProperties actual) {
        assertEquals(new VectorProperties(1, 64, 1000, 5000, 1, 20000, 262144,
                100, 100, 32, 500, 32, 30), actual);
    }
}
