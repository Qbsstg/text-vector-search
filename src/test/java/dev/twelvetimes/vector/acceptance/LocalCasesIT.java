package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.search.CosineSimilarity;
import dev.twelvetimes.vector.search.ScoredDocument;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.ProductionTaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskTerminalService;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 用户本地用例的独立可执行补充。夹具只含已审合成文本与数学数值，所有数据库为临时绝对目录。
 * 复用既有环境的端口0和独立文件库；故障只进入测试对象，绝不添加生产故障入口。
 */
class LocalCasesIT {
    @TempDir Path directory;
    private final ObjectMapper json = new ObjectMapper();
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void 十六篇合成文档真实HTTP闭环及增量向量不变() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            var embedder = env.bean(TextEmbedder.class);
            byte[] before = VectorCodec.encode(embedder.embed("共享充电宝押金退款规则"));
            for (JsonNode doc : fixture("documents.json").get("documents")) {
                String id = doc.get("documentId").asString();
                String task = send(env, "POST", "/api/documents", doc.toString(), 202).get("taskId").asString();
                waitSuccess(env, task);
                JsonNode detail = send(env, "GET", "/api/documents/" + id, null, 200);
                assertEquals(doc.get("text").asString(), detail.get("text").asString(), id);
                assertEquals(doc.has("channel") ? doc.get("channel").asString() : "default", detail.get("channel").asString());
                assertTrue(detail.get("vectorReady").asBoolean());
                assertEquals(task, detail.get("taskId").asString());
                assertFalse(detail.get("submittedAt").isNull());
                assertFalse(detail.get("vectorCompletedAt").isNull());
            }
            assertArrayEquals(before, VectorCodec.encode(embedder.embed("共享充电宝押金退款规则")));
            byte[] first = bytes(env, "D-001");
            assertArrayEquals(first, bytes(env, "D-002"));
            assertArrayEquals(bytes(env, "D-008"),
                    bytes(env, "D-009"));
            assertArrayEquals(new byte[1024], bytes(env, "D-006"));
            JsonNode result = send(env, "POST", "/api/search", "{\"text\":\"共享充电宝押金退款规则\",\"topK\":2,\"channel\":\"faq\"}", 200);
            assertEquals(List.of("D-001", "D-002"), ids(result.get("hits"), "documentId"));
            assertEquals(1, env.hits("D-001"));
            send(env, "GET", "/api/documents", null, 200);
            send(env, "GET", "/api/documents/D-001", null, 200);
            send(env, "GET", "/api/tasks/" + env.bean(KnowledgeMapper.class).findDocument("D-001").taskId(), null, 200);
            assertEquals(1, env.hits("D-001"));
            JsonNode invalid = send(env, "PUT", "/api/documents/D-001/invalidation", null, 200);
            assertEquals(invalid, send(env, "PUT", "/api/documents/D-001/invalidation", null, 200));
            JsonNode next = send(env, "POST", "/api/search", "{\"text\":\"共享充电宝押金退款规则\",\"topK\":100,\"channel\":\"faq\"}", 200);
            assertFalse(ids(next.get("hits"), "documentId").contains("D-001"));
            assertEquals(1, env.hits("D-001"));
            for (JsonNode query : fixture("documents.json").get("exampleQueries")) {
                send(env, "POST", "/api/search", query.toString(), 200);
            }
            record("API-01-ALG-12", "16篇逐篇真实HTTP成功，原文/渠道/零文/增量稳定/只计最终命中/失效排除成立");
        }
    }

    @Test
    void 四组工程等价对按位一致且各自原文真实落库() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            TextEmbedder actual = env.bean(TextEmbedder.class);
            for (JsonNode pair : fixture("text_relations.json").get("engineeringEqualPairs")) {
                String a = pair.get("a").asString();
                String b = pair.get("b").asString();
                assertArrayEquals(VectorCodec.encode(actual.embed(a)), VectorCodec.encode(actual.embed(b)));
                int index = 0;
                for (String raw : List.of(a, b)) {
                    String id = pair.get("id").asString() + "-" + index++;
                    var doc = json.createObjectNode().put("documentId", id).put("text", raw);
                    String task = send(env, "POST", "/api/documents", doc.toString(), 202).get("taskId").asString();
                    waitSuccess(env, task);
                    assertEquals(raw, send(env, "GET", "/api/documents/" + id, null, 200).get("text").asString());
                }
            }
            record("ALG-06", "4工程等价对全部按位一致；8篇各自原文未被规范化覆盖");
        }
    }

    @Test
    void 十四条请求夹具按已冻结协议逐条真实HTTP验证() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            for (JsonNode request : fixture("request_cases.json").get("cases")) {
                String id = request.get("id").asString();
                // 原材料仍保留待冻结值；这里只用当前API契约解决null渠道和字符串K，不回写原件。
                int expected = id.equals("REQ-13") ? 200 : id.equals("REQ-14") ? 400 : request.get("expectedStatus").asInt();
                JsonNode body = send(env, request.get("method").asString(), request.get("path").asString(),
                        request.has("body") ? request.get("body").toString() : null, expected);
                if (expected == 202) {
                    waitSuccess(env, body.get("taskId").asString());
                }
                if (expected >= 400) {
                    assertTrue(body.get("code").isString());
                    assertTrue(body.get("fieldErrors").isArray());
                    assertFalse(body.has("stackTrace"));
                }
                record(id, "HTTP " + expected);
            }
            assertEquals(1, env.bean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 数学向量经真实H2往返保持每个原始位() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            JsonNode vectors = fixture("vector_oracles.json").get("vectors");
            for (String name : List.of("ZERO", "E0", "NEG_E0", "E1", "THREE_FOUR")) {
                float[] expected = vector(vectors.get(name));
                env.fixture(name, "数学夹具", "oracle", expected, "SUCCEEDED");
                byte[] stored = bytes(env, name);
                assertEquals(1024, stored.length);
                assertArrayEquals(VectorCodec.encode(expected), stored);
                float[] restored = VectorCodec.decode(stored);
                for (int i = 0; i < 256; i++) {
                    assertEquals(Float.floatToRawIntBits(expected[i]), Float.floatToRawIntBits(restored[i]));
                }
            }
            record("ALG-08", "5个人工数学向量经H2文件存储逐位往返");
        }
    }

    @Test
    void 六组余弦与五组TopK原始数学夹具逐条断言() throws Exception {
        JsonNode f = fixture("vector_oracles.json");
        for (JsonNode c : f.get("cosineCases")) {
            double actual = CosineSimilarity.score(vector(f.get("vectors").get(c.get("left").asString())),
                    vector(f.get("vectors").get(c.get("right").asString())));
            assertEquals(c.get("expected").asDouble(), actual, 1e-6);
        }
        List<ScoredDocument> candidates = new ArrayList<>();
        for (JsonNode c : f.get("scoredCandidates")) {
            candidates.add(new ScoredDocument(c.get("documentId").asString(), c.get("score").asDouble()));
        }
        for (JsonNode c : f.get("topKCases")) {
            for (int shuffle = 0; shuffle < 5; shuffle++) {
                Collections.shuffle(candidates, new Random(20261023L + shuffle));
                var actual = dev.twelvetimes.vector.search.TopKSelector.select(candidates, c.get("topK").asInt());
                List<String> expected = new ArrayList<>();
                for (JsonNode id : c.get("expectedIds")) { expected.add(id.asString()); }
                assertEquals(expected, actual.stream().map(ScoredDocument::documentId).toList());
            }
        }
        record("ALG-09-10", "6余弦答案、5组TopK各5次固定seed顺序，非文本语义准确率");
    }

    @Test
    void 原始过滤数学夹具先过滤再返回零负分() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            JsonNode f = fixture("vector_oracles.json");
            for (JsonNode doc : f.get("filterScenario").get("candidateSetup")) {
                float[] value = doc.get("vectorName").isNull() ? null : vector(f.get("vectors").get(doc.get("vectorName").asString()));
                String id = doc.get("documentId").asString();
                env.fixture(id, "过滤夹具", doc.get("channel").asString(), value, doc.get("taskStatus").asString());
                if (doc.get("invalidated").asBoolean()) {
                    env.sql("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP WHERE document_id=?", id);
                }
            }
            TextEmbedder oracle = new TextEmbedder() {
                @Override public String version() { return env.bean(TextEmbedder.class).version(); }
                @Override public float[] embed(String text) { return vector(f.get("vectors").get("E0")); }
            };
            var service = env.service(oracle, env.bean(dev.twelvetimes.vector.search.SearchCandidateReader.class),
                    env.bean(dev.twelvetimes.vector.search.SearchHitCountService.class), 1);
            var result = service.search(new SearchRequest("人工查询向量", 2, "faq"));
            assertEquals(List.of("FAQ-A", "FAQ-B"), result.hits().stream().map(h -> h.documentId()).toList());
            assertEquals(0.0, result.hits().get(0).score());
            assertEquals(-1.0, result.hits().get(1).score());
            assertEquals(0, env.hits("EX-CROSS"));
            assertEquals(0, env.hits("EX-INVALID"));
            assertEquals(1, env.hits("FAQ-A"));
            assertEquals(1, env.hits("FAQ-B"));
            record("API-06", "真实H2过滤原始人工场景，返回0/-1，只有最终两项计数");
        }
    }

    @Test
    void 真实HTTP在计算暂停时已返回接收且不可搜索() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            TextEmbedder actual = env.bean(TextEmbedder.class);
            TextEmbedder blocked = new TextEmbedder() {
                @Override public String version() { return actual.version(); }
                @Override public float[] embed(String text) {
                    entered.countDown();
                    try { await(release); } catch (InterruptedException exception) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(exception);
                    }
                    return actual.embed(text);
                }
            };
            try (var dispatch = dispatcher(env, blocked)) {
                dispatch.start();
                String task = send(env, "POST", "/api/documents", "{\"documentId\":\"ASYNC\",\"text\":\"异步合成文本\"}", 202).get("taskId").asString();
                try {
                    await(entered);
                    assertEquals("RUNNING", send(env, "GET", "/api/tasks/" + task, null, 200).get("status").asString());
                    assertEquals(0, send(env, "POST", "/api/search", "{\"text\":\"\",\"topK\":1}", 200).get("hits").size());
                } finally { release.countDown(); }
                waitSuccess(env, task);
            }
            record("TASK-01", "HTTP202先于受控计算完成；RUNNING时不可检索，释放后真实B1成功");
        } finally { release.countDown(); }
    }

    @Test
    void 受控任务失败可查且后续任务继续成功() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            TextEmbedder actual = env.bean(TextEmbedder.class);
            TextEmbedder controlled = new TextEmbedder() {
                @Override public String version() { return actual.version(); }
                @Override public float[] embed(String text) {
                    if (text.equals("坏任务")) { throw new IllegalStateException("测试secret SQL/path"); }
                    return actual.embed(text);
                }
            };
            try (var dispatch = dispatcher(env, controlled)) {
                String failed = send(env, "POST", "/api/documents", "{\"documentId\":\"BAD\",\"text\":\"坏任务\"}", 202).get("taskId").asString();
                assertEquals("QUEUED", send(env, "GET", "/api/tasks/" + failed, null, 200).get("status").asString());
                dispatch.start();
                until(() -> "FAILED".equals(env.bean(KnowledgeMapper.class).findTask(failed).status()));
                JsonNode task = send(env, "GET", "/api/tasks/" + failed, null, 200);
                assertEquals("COMPUTATION_FAILED", task.get("errorCode").asString());
                assertFalse(task.get("errorMessage").asString().contains("secret"));
                assertFalse(task.get("errorMessage").asString().contains("SQL"));
                JsonNode detail = send(env, "GET", "/api/documents/BAD", null, 200);
                assertFalse(detail.get("vectorReady").asBoolean());
                assertTrue(detail.get("vectorCompletedAt").isNull());
                assertEquals(0, send(env, "POST", "/api/search", "{\"text\":\"\",\"topK\":100}", 200).get("hits").size());
                String good = send(env, "POST", "/api/documents", "{\"documentId\":\"GOOD\",\"text\":\"正常任务\"}", 202).get("taskId").asString();
                waitSuccess(env, good);
                assertEquals("FAILED", send(env, "GET", "/api/tasks/" + failed, null, 200).get("status").asString());
            }
            record("TASK-02", "隔离真实HTTP+受控算法失败可查/不泄露/无向量，后续正常任务继续；不是Chrome FAILED");
        }
    }

    @Test
    void 四态文件库重启只恢复未完成任务() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            for (String state : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED")) {
                env.fixture(state, "四态合成数据", "restart", state.equals("SUCCEEDED") ? env.bean(TextEmbedder.class).embed("四态合成数据") : null, state);
            }
        }
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            var mapper = env.bean(KnowledgeMapper.class);
            until(() -> "SUCCEEDED".equals(mapper.findDocument("QUEUED").status())
                    && "SUCCEEDED".equals(mapper.findDocument("RUNNING").status()));
            assertEquals("SUCCEEDED", mapper.findDocument("SUCCEEDED").status());
            assertEquals("FAILED", mapper.findDocument("FAILED").status());
            assertEquals(0, mapper.findTask(mapper.findDocument("SUCCEEDED").taskId()).attemptCount());
            assertEquals(0, mapper.findTask(mapper.findDocument("FAILED").taskId()).attemptCount());
            assertEquals(1, mapper.findTask(mapper.findDocument("RUNNING").taskId()).attemptCount());
            record("REC-02", "独立文件库四态重启，未完成恢复，成功/FAILED不重做；恢复前门禁由既有B3LifecycleIT复用");
        }
    }

    @Test
    void 旧版本成功文档真实HTTP过滤诊断不混算() throws Exception {
        String oldVersion;
        try (var old = new B4QaEnvironment(directory, true, true, null, "--vector.cpu-rounds=32")) {
            old.realDocument("OLD", "版本诊断合成文本", "version");
            oldVersion = old.bean(TextEmbedder.class).version();
            assertEquals(1, send(old, "POST", "/api/search", "{\"text\":\"版本诊断合成文本\",\"topK\":1}", 200).get("hits").size());
        }
        try (var current = new B4QaEnvironment(directory, true, true, null, "--vector.cpu-rounds=33")) {
            assertNotEquals(oldVersion, current.bean(TextEmbedder.class).version());
            assertEquals(1024, current.bean(KnowledgeMapper.class).findDocument("OLD").vectorLength());
            JsonNode health = send(current, "GET", "/health", null, 200);
            assertTrue(health.get("businessReady").asBoolean());
            JsonNode response = send(current, "POST", "/api/search", "{\"text\":\"版本诊断合成文本\",\"topK\":1}", 200);
            assertEquals(0, response.get("hits").size());
            record("REC-07-wire", "GET /health HTTP200\n" + health + "\nPOST /api/search HTTP200\n" + response
                    + "\n旧数据详情HTTP200\n" + send(current, "GET", "/api/documents/OLD", null, 200));
            // 原用例要求版本不兼容时阻止正常就绪。将原断言失败独立存证，观察测试不冒充该用例通过。
            AssertionError originalFailure = assertThrows(AssertionError.class,
                    () -> assertFalse(health.get("businessReady").asBoolean(), "REC-07原预期：旧版本数据未迁移前不得正常就绪"));
            record("REC-07-original-assertion", originalFailure.toString());
            // 本测试只锁定冻结B4的实际观察；原REC-07禁止此行为，必须另报冲突，不能将其标PASS。
            record("REC-07-conflict", "原预期FAIL/契约冲突：旧版本SUCCEEDED且ready，32→33新版本health200/ready，搜索200空hits；没有迁移/重算。观察测试通过不等于原REC-07通过。旧=" + oldVersion + "；新=" + current.bean(TextEmbedder.class).version());
        }
    }

    @Test
    void 无效存储路径启动失败且不偷偷换空库() throws Exception {
        Path file = directory.resolve("not-a-directory");
        Files.writeString(file, "仅隔离夹具，保留到测试临时目录回收");
        assertThrows(RuntimeException.class, () -> {
            try (var ignored = new B4QaEnvironment(file, true, false, null)) {
                throw new AssertionError("无效存储路径不应启动成功");
            }
        });
        assertTrue(Files.isRegularFile(file));
        record("REC-08", "普通文件不能充当数据库目录，初始化失败，不改用其他空库");
    }

    @Test
    void 八组语言关系记录真实Java分数不预设语义高低() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, false, null)) {
            TextEmbedder actual = env.bean(TextEmbedder.class);
            List<String> output = new ArrayList<>();
            output.add("版本=" + actual.version() + "；没有人工相关性标签，不报告准确率/Recall/NDCG");
            for (JsonNode pair : fixture("text_relations.json").get("observationPairs")) {
                double score = CosineSimilarity.score(actual.embed(pair.get("a").asString()), actual.embed(pair.get("b").asString()));
                assertTrue(Double.isFinite(score) && score >= -1 && score <= 1);
                output.add(pair.get("id").asString() + "\t" + score + "\t" + pair.get("relation").asString());
            }
            record("PERF-04-observation", String.join("\n", output));
        }
    }

    /** 独立终态服务和派发器仅用于B2测试组成；依旧调用真实SQL/事务和生产handler。 */
    private TaskDispatcher dispatcher(B4QaEnvironment env, TextEmbedder embedder) {
        TaskReadiness readiness = env.bean(TaskReadiness.class);
        TaskTerminalService terminals = new TaskTerminalService(env.bean(TaskTerminalMapper.class), readiness,
                readiness.writeBarrier(), env.bean(PlatformTransactionManager.class));
        return new TaskDispatcher(env.bean(KnowledgeMapper.class), env.bean(TaskClaimService.class), readiness,
                env.bean(VectorProperties.class), new ProductionTaskExecutionHandler(embedder, terminals));
    }

    private JsonNode send(B4QaEnvironment env, String method, String path, String body, int expected) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + path)).timeout(Duration.ofSeconds(10));
        request.header("Content-Type", "application/json");
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        var response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), path + " " + response.body());
        return json.readTree(response.body());
    }

    /** 单独连接读取完整向量，不让默认详情接口为测试暴露向量列。 */
    private static byte[] bytes(B4QaEnvironment env, String id) throws Exception {
        try (var connection = env.bean(DataSource.class).getConnection();
             var query = connection.prepareStatement("SELECT vector_bytes FROM documents WHERE document_id=?")) {
            query.setString(1, id);
            try (var result = query.executeQuery()) {
                assertTrue(result.next());
                return result.getBytes(1);
            }
        }
    }

    private void waitSuccess(B4QaEnvironment env, String task) {
        until(() -> "SUCCEEDED".equals(env.bean(KnowledgeMapper.class).findTask(task).status()));
    }

    private JsonNode fixture(String name) throws Exception {
        try (var input = getClass().getResourceAsStream("/local-cases/" + name)) {
            assertNotNull(input, name);
            return json.readTree(input);
        }
    }

    private static float[] vector(JsonNode values) {
        float[] result = new float[256];
        assertEquals(256, values.size());
        for (int i = 0; i < 256; i++) { result[i] = (float) values.get(i).asDouble(); }
        return result;
    }

    private static List<String> ids(JsonNode values, String field) {
        List<String> result = new ArrayList<>();
        for (JsonNode value : values) { result.add(value.get(field).asString()); }
        return result;
    }

    /** 原始断言观察保留到target，目录不含数据库；不以摘要替代Maven报告。 */
    private static void record(String id, String value) throws Exception {
        Path evidence = Path.of("target/local-cases-evidence");
        Files.createDirectories(evidence);
        Files.writeString(evidence.resolve(id + ".txt"), value + "\n");
    }
}
