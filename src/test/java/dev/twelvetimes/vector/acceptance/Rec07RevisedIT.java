package dev.twelvetimes.vector.acceptance;

import com.zaxxer.hikari.HikariDataSource;
import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.search.CosineSimilarity;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.sql.Connection;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DEC-REC07-20261023-01修订判据的独立实测，不替换LocalCasesIT旧诊断或原始FAIL。
 * 真实R32数据与四版本夹具分别使用独立临时文件库；同库的两个生产组成只顺序开启。
 * 证据仅记录合成ID、全字段元信息及完整原文/向量SHA，不公开原文或向量载荷。
 */
class Rec07RevisedIT {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void R32真实旧库经R33顺序重启全字段不变且当前业务可用() throws Exception {
        Path data = ownedDirectory();
        Path evidence = evidenceDirectory("real-old-database");
        List<String> oldIds = List.of("REC07-OLD-TEXT", "REC07-OLD-INVALID", "REC07-OLD-ZERO");
        Map<String, String> texts = Map.of(oldIds.get(0), "R32合成旧文完整原文：" + "段落😀".repeat(300),
                oldIds.get(1), "R32已失效合成文档", oldIds.get(2), "");
        Map<String, StoredDocument> original;
        String oldVersion;
        try (var old = start(data, 32)) {
            oldVersion = old.bean(TextEmbedder.class).version();
            assertTrue(oldVersion.contains("-r32-"));
            assertHealthy(old, oldVersion);
            for (String id : oldIds) { submitAndComplete(old, id, texts.get(id), "rec07-old"); }
            // 用真实业务路径产生非零计数和首次失效时间，不能靠版本夹具伪造真实旧行。
            assertEquals(3, search(old, "", 100, "rec07-old").get("hits").size());
            assertEquals(List.of(oldIds.get(0)), ids(search(old, texts.get(oldIds.get(0)), 1, "rec07-old")));
            send(old, "PUT", "/api/documents/" + oldIds.get(1) + "/invalidation", null, 200);
            original = snapshot(old, oldIds);
            for (String id : oldIds) {
                StoredDocument row = original.get(id);
                assertEquals("SUCCEEDED", row.task().get("status"));
                assertEquals("1", row.task().get("attempt_count"));
                assertNotNull(row.task().get("run_token"));
                assertNotNull(row.task().get("started_at"));
                assertNotNull(row.task().get("finished_at"));
                assertEquals(oldVersion, row.task().get("embedding_version"));
                assertEquals(oldVersion, row.document().get("embedding_version"));
                assertTrue(row.document().get("vector_bytes").endsWith(";bytes=1024"));
                JsonNode detail = send(old, "GET", "/api/documents/" + id, null, 200);
                assertEquals(sha256(texts.get(id).getBytes(StandardCharsets.UTF_8)),
                        sha256(detail.get("text").asString().getBytes(StandardCharsets.UTF_8)));
            }
            assertEquals("2", original.get(oldIds.get(0)).document().get("hit_count"));
            assertTrue(Boolean.parseBoolean(original.get(oldIds.get(1)).document().get("invalidated")));
            write(evidence.resolve("r32-before-normal-close.json"), original);
        }
        assertTrue(Files.isRegularFile(data.resolve("knowledge.mv.db")));
        // 上一个close已经返回，才打开同一绝对路径；不并行挂同一嵌入式H2。
        try (var current = start(data, 33)) {
            String currentVersion = current.bean(TextEmbedder.class).version();
            assertNotEquals(oldVersion, currentVersion);
            JsonNode health = assertHealthy(current, currentVersion);
            write(evidence.resolve("r33-all-old-health.json"), health);
            Map<String, StoredDocument> afterStartup = snapshot(current, oldIds);
            write(evidence.resolve("r33-after-startup.json"), afterStartup);
            assertEquals(original, afterStartup, "每个旧行全部列、全文和完整向量摘要必须保持，不能迁移或重领");
            assertEquals(0, search(current, "旧版本查询", 100, null).get("hits").size());
            for (String id : oldIds) {
                JsonNode detail = send(current, "GET", "/api/documents/" + id, null, 200);
                assertTrue(detail.get("vectorReady").asBoolean());
                assertEquals(texts.get(id), detail.get("text").asString());
                JsonNode task = send(current, "GET", "/api/tasks/" + original.get(id).task().get("task_id"), null, 200);
                assertEquals("SUCCEEDED", task.get("status").asString());
            }
            String newId = "REC07-NEW-R33";
            submitAndComplete(current, newId, "R33当前版本合成新文", "rec07-current");
            JsonNode result = search(current, "R33当前版本合成新文", 100, null);
            assertEquals(List.of(newId), ids(result));
            assertEquals(1.0, result.get("hits").get(0).get("score").asDouble(), 1e-12);
            assertEquals(1, current.hits(newId));
            StoredDocument newRow = snapshot(current, List.of(newId)).get(newId);
            assertEquals(currentVersion, newRow.document().get("embedding_version"));
            assertEquals(currentVersion, newRow.task().get("embedding_version"));
            Map<String, StoredDocument> afterNewBusiness = snapshot(current, oldIds);
            write(evidence.resolve("r33-after-new-business-old-rows.json"), afterNewBusiness);
            write(evidence.resolve("r33-new-row.json"), newRow);
            assertEquals(original, afterNewBusiness, "新版本接收/检索不应重写或计数旧版本行");
            assertHealthy(current, currentVersion);
            write(evidence.resolve("conclusion.json"), Map.of("caseId", "REC-07-R1", "status", "PASS",
                    "mode", "REAL_HTTP_REAL_H2_REAL_B1", "oldRows", oldIds.size(), "sameFileSequentialRestart", true,
                    "oldVersion", oldVersion, "currentVersion", currentVersion, "oldAllColumnsUnchanged", true));
        }
    }

    @Test
    void 文档任务双版本四组合在TopK前过滤且只计合格行() throws Exception {
        Path data = ownedDirectory();
        Path evidence = evidenceDirectory("four-version-fixtures");
        try (var env = start(data, 33)) {
            String current = env.bean(TextEmbedder.class).version();
            String old = "char12-sha256-r32-nfkc-root-ws-v1";
            assertNotEquals(current, old);
            String query = "REC07四版本矩阵真实查询向量";
            float[] high = env.bean(TextEmbedder.class).embed(query);
            List<String> ids = List.of("FIX-DOC-NEW-TASK-OLD", "FIX-DOC-OLD-TASK-NEW", "FIX-BOTH-OLD", "FIX-BOTH-NEW");
            for (String id : ids) {
                // 成功状态/人工向量只为资格矩阵造数，不能冒充真实旧业务行；原文不是向量的语义oracle。
                env.fixture(id, "独立版本矩阵合成夹具", "rec07-matrix",
                        id.equals("FIX-BOTH-NEW") ? new float[256] : high, "SUCCEEDED");
            }
            // 只修改本方法专用夹具的版本字段，真实R32旧库在另一独立测试内保持不可变。
            env.sql("UPDATE embedding_tasks SET embedding_version=? WHERE document_id=?", old, ids.get(0));
            env.sql("UPDATE documents SET embedding_version=? WHERE document_id=?", old, ids.get(1));
            env.sql("UPDATE documents SET embedding_version=? WHERE document_id=?", old, ids.get(2));
            env.sql("UPDATE embedding_tasks SET embedding_version=? WHERE document_id=?", old, ids.get(2));
            var before = snapshot(env, ids);
            write(evidence.resolve("four-combinations-before.json"), before);
            assertEquals(List.of(current, old), versions(before.get(ids.get(0))));
            assertEquals(List.of(old, current), versions(before.get(ids.get(1))));
            assertEquals(List.of(old, old), versions(before.get(ids.get(2))));
            assertEquals(List.of(current, current), versions(before.get(ids.get(3))));
            assertEquals(1.0, CosineSimilarity.score(high, high), 1e-12);
            JsonNode topOne = search(env, query, 1, "rec07-matrix");
            assertEquals(List.of("FIX-BOTH-NEW"), ids(topOne));
            assertEquals(0.0, topOne.get("hits").get(0).get("score").asDouble());
            assertEquals(List.of("FIX-BOTH-NEW"), ids(search(env, query, 100, "rec07-matrix")));
            var after = snapshot(env, ids);
            write(evidence.resolve("four-combinations-after.json"), after);
            for (int i = 0; i < 3; i++) {
                assertEquals(0, env.hits(ids.get(i)));
                assertEquals(before.get(ids.get(i)), after.get(ids.get(i)), "排除行全部列保持，不能混算/迁移/计数");
            }
            assertEquals(2, env.hits("FIX-BOTH-NEW"));
            assertHealthy(env, current);
            write(evidence.resolve("conclusion.json"), Map.of("caseId", "REC-07-R1", "status", "PASS",
                    "mode", "REAL_HTTP_REAL_H2_EXPLICIT_VERSION_FIXTURES", "combinations", 4,
                    "excludedHighScore", 1, "eligibleScore", 0, "topK", 1, "filterBeforeTopK", true));
        }
    }

    /** 全部资源使用生产冻结默认值；只有CPU版本32/33、回环随机端口与临时数据路径是测试参数。 */
    private RevisedEnvironment start(Path data, int rounds) throws Exception {
        var env = new RevisedEnvironment(data, rounds);
        try {
            VectorProperties properties = env.bean(VectorProperties.class);
            assertEquals(1, properties.workerCount());
            assertEquals(64, properties.queueCapacity());
            assertEquals(1000, properties.maxPendingTasks());
            assertEquals(5000, properties.maxDocuments());
            assertEquals(1, properties.searchPermits());
            assertEquals(20000, properties.maxTextCodePoints());
            assertEquals(262144, properties.maxRequestBytes());
            assertEquals(100, properties.maxTopK());
            assertEquals(100, properties.maxPageSize());
            assertEquals(500, properties.scanIntervalMs());
            assertEquals(32, properties.scanBatchSize());
            assertEquals(30, properties.shutdownWaitSeconds());
            assertEquals(rounds, properties.cpuRounds());
            int poolSize = ((HikariDataSource) env.bean(DataSource.class)).getMaximumPoolSize();
            assertEquals(4, poolSize);
            write(evidenceDirectory("runtime-r" + rounds).resolve("frozen-resources.json"),
                    Map.of("vectorProperties", properties, "hikariMaximumPoolSize", poolSize,
                            "embeddingVersion", env.bean(TextEmbedder.class).version(),
                            "temporaryAbsoluteDataDirectory", data.isAbsolute(), "randomLoopbackPort", env.port()));
            return env;
        } catch (Exception | AssertionError failure) {
            env.close();
            throw failure;
        }
    }

    private Path ownedDirectory() throws Exception {
        Path data = directory.toRealPath();
        assertTrue(data.isAbsolute());
        assertFalse(Files.isSymbolicLink(directory));
        try (var entries = Files.list(data)) { assertEquals(0, entries.count()); }
        return data;
    }

    private JsonNode assertHealthy(RevisedEnvironment env, String version) throws Exception {
        JsonNode health = send(env, "GET", "/health", null, 200);
        assertTrue(health.get("businessReady").asBoolean());
        assertTrue(health.get("taskReady").asBoolean());
        assertEquals("READY", health.get("taskState").asString());
        assertEquals(version, health.get("embeddingVersion").asString());
        for (String check : List.of("databaseReady", "databaseWritable", "dispatcherReady", "algorithmReady", "searchReady", "routesReady")) {
            assertTrue(health.get("checks").get(check).asBoolean(), check);
        }
        assertEquals(TaskReadiness.State.READY, env.bean(TaskReadiness.class).state());
        return health;
    }

    private void submitAndComplete(RevisedEnvironment env, String id, String text, String channel) throws Exception {
        var body = json.createObjectNode().put("documentId", id).put("text", text).put("channel", channel);
        JsonNode accepted = send(env, "POST", "/api/documents", body.toString(), 202);
        assertEquals("QUEUED", accepted.get("status").asString());
        String task = accepted.get("taskId").asString();
        until(() -> "SUCCEEDED".equals(env.bean(KnowledgeMapper.class).findTask(task).status()));
        assertEquals("SUCCEEDED", send(env, "GET", "/api/tasks/" + task, null, 200).get("status").asString());
        JsonNode detail = send(env, "GET", "/api/documents/" + id, null, 200);
        assertEquals(text, detail.get("text").asString());
        assertTrue(detail.get("vectorReady").asBoolean());
    }

    private JsonNode search(RevisedEnvironment env, String text, int topK, String channel) throws Exception {
        var body = json.createObjectNode().put("text", text).put("topK", topK);
        if (channel != null) { body.put("channel", channel); }
        return send(env, "POST", "/api/search", body.toString(), 200);
    }

    private JsonNode send(RevisedEnvironment env, String method, String path, String body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + path));
        request.timeout(Duration.ofSeconds(10)).header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var result = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, result.statusCode(), method + " " + path);
        return json.readTree(result.body());
    }

    private static List<String> ids(JsonNode result) {
        List<String> values = new ArrayList<>();
        for (JsonNode hit : result.get("hits")) { values.add(hit.get("documentId").asString()); }
        return values;
    }

    private static List<String> versions(StoredDocument row) {
        return List.of(row.document().get("embedding_version"), row.task().get("embedding_version"));
    }

    /** SELECT *逐列取快照，包含token/attempt/所有时间和错误字段；大载荷只以全量字节摘要表示。 */
    private Map<String, StoredDocument> snapshot(RevisedEnvironment env, List<String> ids) throws Exception {
        Map<String, StoredDocument> snapshots = new LinkedHashMap<>();
        try (var connection = env.bean(DataSource.class).getConnection()) {
            for (String id : ids) {
                Map<String, String> document = row(connection, "documents", id);
                Map<String, String> task = row(connection, "embedding_tasks", id);
                assertEquals(Set.of("document_id", "raw_text", "channel", "submitted_at", "invalidated", "invalidated_at",
                        "vector_bytes", "embedding_version", "vector_completed_at", "hit_count"), document.keySet());
                assertEquals(Set.of("task_id", "document_id", "status", "embedding_version", "run_token", "attempt_count",
                        "submitted_at", "started_at", "finished_at", "error_code", "error_message"), task.keySet());
                snapshots.put(id, new StoredDocument(document, task));
            }
        }
        return snapshots;
    }

    private Map<String, String> row(Connection connection, String table, String id) throws Exception {
        Map<String, String> values = new LinkedHashMap<>();
        // 表名来自本类固定两表列表，外部值只用绑定参数。
        try (var query = connection.prepareStatement("SELECT * FROM " + table + " WHERE document_id=?")) {
            query.setString(1, id);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                for (int index = 1; index <= result.getMetaData().getColumnCount(); index++) {
                    String name = result.getMetaData().getColumnLabel(index).toLowerCase(Locale.ROOT);
                    String value;
                    if (name.equals("vector_bytes")) {
                        byte[] bytes = result.getBytes(index);
                        value = bytes == null ? null : "sha256:" + sha256(bytes) + ";bytes=" + bytes.length;
                    } else if (name.equals("raw_text")) {
                        String text = result.getString(index);
                        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
                        value = "sha256:" + sha256(bytes) + ";utf8Bytes=" + bytes.length
                                + ";codePoints=" + text.codePointCount(0, text.length());
                    } else {
                        value = result.getString(index);
                    }
                    values.put(name, value);
                }
                assertFalse(result.next());
            }
        }
        return values;
    }

    private Path evidenceDirectory(String name) throws Exception {
        Path output = Path.of("target/rec07-revised-evidence").resolve(name + "-" + UUID.randomUUID());
        return Files.createDirectories(output);
    }

    private void write(Path path, Object value) throws Exception {
        Files.writeString(path, json.writerWithDefaultPrettyPrinter().writeValueAsString(value) + "\n");
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    /** 与原B4测试帮助类分离，避免其固定50ms参数与冻结500ms发生重复CLI合并。 */
    private static final class RevisedEnvironment implements AutoCloseable {
        private final ConfigurableApplicationContext context;

        RevisedEnvironment(Path data, int rounds) {
            context = new SpringApplication(VectorSearchApplication.class).run(
                    "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                    "--server.address=127.0.0.1", "--server.port=0", "--APP_DATA_DIR=" + data.toAbsolutePath(),
                    "--spring.datasource.username=sa", "--spring.datasource.password=", "--vector.cpu-rounds=" + rounds);
        }

        <T> T bean(Class<T> type) { return context.getBean(type); }

        int port() { return ((WebServerApplicationContext) context).getWebServer().getPort(); }

        long hits(String id) { return bean(KnowledgeMapper.class).findDocument(id).hitCount(); }

        void sql(String sql, Object... arguments) throws Exception {
            try (var connection = bean(DataSource.class).getConnection(); var statement = connection.prepareStatement(sql)) {
                for (int i = 0; i < arguments.length; i++) { statement.setObject(i + 1, arguments[i]); }
                assertEquals(1, statement.executeUpdate());
            }
        }

        /** 只给四组合矩阵造数；本类真实R32旧库从不调用此入口。 */
        void fixture(String id, String text, String channel, float[] vector, String status) throws Exception {
            assertEquals("SUCCEEDED", status);
            String version = bean(TextEmbedder.class).version();
            try (var connection = bean(DataSource.class).getConnection()) {
                connection.setAutoCommit(false);
                try (var document = connection.prepareStatement("INSERT INTO documents(document_id,raw_text,channel,submitted_at,vector_bytes,embedding_version,vector_completed_at) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,CURRENT_TIMESTAMP)");
                     var task = connection.prepareStatement("INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,run_token,attempt_count,submitted_at,started_at,finished_at) VALUES(?,?,'SUCCEEDED',?,?,1,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                    document.setString(1, id);
                    document.setString(2, text);
                    document.setString(3, channel);
                    document.setBytes(4, VectorCodec.encode(vector));
                    document.setString(5, version);
                    assertEquals(1, document.executeUpdate());
                    task.setString(1, UUID.randomUUID().toString());
                    task.setString(2, id);
                    task.setString(3, version);
                    task.setString(4, UUID.randomUUID().toString());
                    assertEquals(1, task.executeUpdate());
                    connection.commit();
                } catch (Exception failure) {
                    connection.rollback();
                    throw failure;
                }
            }
        }

        @Override public void close() { context.close(); }
    }

    private record StoredDocument(Map<String, String> document, Map<String, String> task) { }
}
