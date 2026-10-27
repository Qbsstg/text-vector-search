package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.DocumentRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskTerminalService;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 独立HTTP验收：数据库原始字节为预期依据；人工损坏与特殊数值只注入临时文件库。 */
class VectorInformationIT {
    @TempDir Path directory;
    private static volatile Control control;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();

    @Test
    void 真实同文不同ID不去重且完整256维与原始字节一致() throws Exception {
        Control observed = new Control();
        control = observed;
        try (var env = new B4QaEnvironment(directory, true, true, Hooks.class, "--qa.vector-info-hooks=true")) {
            String first = submit(env, "same-a", "同一段中文 ababa 😀");
            String second = submit(env, "same-b", "同一段中文 ababa 😀");
            assertFalse(first.equals(second));
            assertEquals(2, env.bean(KnowledgeMapper.class).countDocuments());
            byte[] bytes = bytes(env, "same-a");
            assertArrayEquals(bytes, bytes(env, "same-b"));
            var before = env.bean(KnowledgeMapper.class).findDocument("same-a");
            int computations = observed.embeds.get();
            assertEquals(2, computations, "计数钩子必须观测到两次真实B1执行");
            int reads = observed.detailQueries.get();
            var a = get(env, "/api/documents/same-a", 200);
            var b = get(env, "/api/documents/same-b", 200);
            verifyVector(a, bytes, storedVersion(env, "same-a"));
            verifyVector(b, bytes, storedVersion(env, "same-b"));
            assertEquals(a.get("vectorInfo").get("sha256"), b.get("vectorInfo").get("sha256"));
            assertFalse(a.get("vectorInfo").get("zeroVector").asBoolean());
            for (int index = 0; index < 3; index++) {
                get(env, "/api/documents/same-a", 200);
                var task = get(env, "/api/tasks/" + first, 200);
                assertTrue(task.get("document").get("vectorInfo").isNull());
                assertEquals(a.get("text"), task.get("document").get("text"));
                var list = get(env, "/api/documents", 200);
                assertEquals(2, list.get("totalElements").asInt());
                for (var item : list.get("items")) {
                    assertFalse(item.has("vectorInfo"));
                    assertTrue(item.get("hitCount").isString());
                }
            }
            assertEquals(reads + 5, observed.detailQueries.get(), "仅详情GET执行专用向量SQL");
            assertEquals(computations, observed.embeds.get(), "详情与轻量轮询不调用embed");
            assertEquals(before, env.bean(KnowledgeMapper.class).findDocument("same-a"));
            assertArrayEquals(bytes, bytes(env, "same-a"));
        } finally { control = null; }
    }

    @Test
    void 空文与纯空白真实成功且全零仍然就绪() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            submit(env, "empty", "");
            submit(env, "whitespace", " \t\n　");
            for (String id : List.of("empty", "whitespace")) {
                byte[] stored = bytes(env, id);
                assertArrayEquals(new byte[1024], stored);
                var detail = get(env, "/api/documents/" + id, 200);
                verifyVector(detail, stored, storedVersion(env, id));
                assertTrue(detail.get("vectorInfo").get("zeroVector").asBoolean());
                assertEquals("SUCCEEDED", detail.get("taskStatus").asString());
            }
        }
    }

    @Test
    void 三种非成功状态不制造向量且任务document为null() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
                String id = status.toLowerCase(java.util.Locale.ROOT);
                // 额外植入字节也不能绕过任务终态；它是人工异常夹具，不冒充真实处理结果。
                env.fixture(id, "状态夹具", "default", new float[256], status);
                var detail = get(env, "/api/documents/" + id, 200);
                assertEquals(status, detail.get("taskStatus").asString());
                assertFalse(detail.get("vectorReady").asBoolean());
                assertTrue(detail.get("vectorInfo").isNull());
                String taskId = env.bean(KnowledgeMapper.class).findDocument(id).taskId();
                assertTrue(get(env, "/api/tasks/" + taskId, 200).get("document").isNull());
            }
        }
    }

    @Test
    void 失效仍保留历史向量且重复读取不改变计数与原文() throws Exception {
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            submit(env, "history", "失效历史 ababa");
            var before = get(env, "/api/documents/history", 200);
            byte[] original = bytes(env, "history");
            request(env, "PUT", "/api/documents/history/invalidation", null, 200);
            var invalidated = env.bean(KnowledgeMapper.class).findDocument("history");
            for (int index = 0; index < 3; index++) {
                var after = get(env, "/api/documents/history", 200);
                assertTrue(after.get("invalidated").asBoolean());
                assertTrue(after.get("vectorReady").asBoolean());
                assertEquals(before.get("vectorInfo"), after.get("vectorInfo"));
                assertEquals(before.get("text"), after.get("text"));
            }
            assertEquals(invalidated, env.bean(KnowledgeMapper.class).findDocument("history"));
            assertArrayEquals(original, bytes(env, "history"));
        }
    }

    @Test
    void 人工极值负零与历史存储版本经HTTP完整往返() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            float[] vector = new float[256];
            float[] special = {-0.0f, Float.MIN_VALUE, -Float.MIN_VALUE, Float.MAX_VALUE,
                    -Float.MAX_VALUE, Float.MIN_NORMAL, 0.1f, -0.12345679f};
            System.arraycopy(special, 0, vector, 0, special.length);
            env.fixture("precision", "与保存向量无关的原文", "default", vector, "SUCCEEDED");
            env.sql("UPDATE documents SET embedding_version='saved-history-v0',hit_count=9007199254740993 WHERE document_id='precision'");
            env.sql("UPDATE embedding_tasks SET embedding_version='different-task-version' WHERE document_id='precision'");
            var detail = get(env, "/api/documents/precision", 200);
            verifyVector(detail, VectorCodec.encode(vector), "saved-history-v0");
            assertEquals("9007199254740993", detail.get("hitCount").asString());
            assertFalse(detail.get("vectorInfo").get("zeroVector").asBoolean());
            float[] negativeZero = new float[256];
            negativeZero[255] = -0.0f;
            env.fixture("negative-zero", "非空原文", "default", negativeZero, "SUCCEEDED");
            var zero = get(env, "/api/documents/negative-zero", 200);
            verifyVector(zero, VectorCodec.encode(negativeZero), storedVersion(env, "negative-zero"));
            assertTrue(zero.get("vectorInfo").get("zeroVector").asBoolean());
            assertFalse(zero.get("vectorInfo").get("sha256").asString().equals(sha(new byte[1024])));
        }
    }

    @Test
    void 非有限坏向量返回统一500且不改数据或伪造占位() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, true, null)) {
            int index = 0;
            for (int bits : new int[] {0x7fc00000, 0x7f800000, 0xff800000}) {
                String id = "bad-" + index++;
                env.fixture(id, "坏向量夹具", "default", new float[256], "SUCCEEDED");
                byte[] bad = new byte[1024];
                ByteBuffer.wrap(bad).order(ByteOrder.BIG_ENDIAN).putInt(4 * 255, bits);
                env.sql("UPDATE documents SET vector_bytes=? WHERE document_id=?", bad, id);
                DocumentRow before = env.bean(KnowledgeMapper.class).findDocument(id);
                var error = get(env, "/api/documents/" + id, 500);
                assertEquals("INTERNAL_ERROR", error.get("code").asString());
                assertEquals(0, error.get("fieldErrors").size());
                assertFalse(error.has("vectorInfo"));
                assertFalse(error.has("stackTrace"));
                assertFalse(error.toString().contains("NaN"));
                assertEquals(before, env.bean(KnowledgeMapper.class).findDocument(id));
                assertArrayEquals(bad, bytes(env, id));
            }
        }
    }

    @Test
    void 单条SQL已读快照与并发失效计数提交不拼接且跨请求不串文档() throws Exception {
        Control observed = new Control();
        control = observed;
        try (var env = new B4QaEnvironment(directory, false, true, Hooks.class, "--qa.vector-info-hooks=true")) {
            env.fixture("snapshot-a", "快照A", "default", new float[256], "SUCCEEDED");
            float[] alternate = new float[256];
            alternate[0] = 1.0f;
            env.fixture("snapshot-b", "快照B", "default", alternate, "SUCCEEDED");
            int before = observed.detailQueries.get();
            observed.blockNext.set(true);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var old = executor.submit(() -> get(env, "/api/documents/snapshot-a", 200));
                await(observed.snapshotRead);
                // A响应在SQL完成后暂停。B仍可独立读取，且A的新已提交状态不应混入旧响应。
                var b = get(env, "/api/documents/snapshot-b", 200);
                assertEquals("snapshot-b", b.get("documentId").asString());
                verifyVector(b, VectorCodec.encode(alternate), storedVersion(env, "snapshot-b"));
                env.sql("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP,hit_count=7 WHERE document_id='snapshot-a'");
                observed.release.countDown();
                var a = old.get(8, TimeUnit.SECONDS);
                assertEquals("snapshot-a", a.get("documentId").asString());
                assertFalse(a.get("invalidated").asBoolean());
                assertTrue(a.get("invalidatedAt").isNull());
                assertEquals("0", a.get("hitCount").asString());
                verifyVector(a, new byte[1024], storedVersion(env, "snapshot-a"));
                var fresh = get(env, "/api/documents/snapshot-a", 200);
                assertTrue(fresh.get("invalidated").asBoolean());
                assertEquals("7", fresh.get("hitCount").asString());
                assertEquals(before + 3, observed.detailQueries.get(), "三次详情各执行一次真实MyBatis查询");
            } finally {
                observed.release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
            }
        } finally { control = null; }
    }

    @Test
    void 真实终态事务提交后旧RUNNING读取不拼入新向量() throws Exception {
        Control observed = new Control();
        control = observed;
        try (var env = new B4QaEnvironment(directory, false, true, Hooks.class, "--qa.vector-info-hooks=true")) {
            env.fixture("terminal", "受控终态夹具", "default", null, "QUEUED");
            String taskId = env.bean(KnowledgeMapper.class).findDocument("terminal").taskId();
            var execution = env.bean(TaskClaimService.class).claim(taskId).orElseThrow();
            observed.blockNext.set(true);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var prior = executor.submit(() -> get(env, "/api/documents/terminal", 200));
                await(observed.snapshotRead);
                // 调用真实终态服务，以其原短事务原子写入文档向量与任务SUCCEEDED。
                var readiness = env.bean(TaskReadiness.class);
                var terminal = new TaskTerminalService(env.bean(TaskTerminalMapper.class), readiness,
                        readiness.writeBarrier(), env.bean(PlatformTransactionManager.class));
                terminal.succeed(execution, new float[256]);
                observed.release.countDown();
                var running = prior.get(8, TimeUnit.SECONDS);
                assertEquals("RUNNING", running.get("taskStatus").asString());
                assertFalse(running.get("vectorReady").asBoolean());
                assertTrue(running.get("vectorInfo").isNull());
                assertTrue(running.get("vectorCompletedAt").isNull());
                var succeeded = get(env, "/api/documents/terminal", 200);
                assertEquals("SUCCEEDED", succeeded.get("taskStatus").asString());
                verifyVector(succeeded, new byte[1024], storedVersion(env, "terminal"));
                assertEquals(2, observed.detailQueries.get());
                assertEquals(0, observed.embeds.get(), "人工向量夹具不声称执行了B1");
            } finally {
                observed.release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
            }
        } finally { control = null; }
    }

    @Test
    void 隔离文件库正常重启仍读取原字节且不重新执行任务() throws Exception {
        byte[] original;
        JsonNode detail;
        DocumentRow before;
        try (var env = new B4QaEnvironment(directory, true, true, null)) {
            submit(env, "restart", "重启持久向量 😀");
            original = bytes(env, "restart");
            detail = get(env, "/api/documents/restart", 200);
            before = env.bean(KnowledgeMapper.class).findDocument("restart");
        }
        Control observed = new Control();
        control = observed;
        try (var env = new B4QaEnvironment(directory, true, true, Hooks.class, "--qa.vector-info-hooks=true")) {
            var after = get(env, "/api/documents/restart", 200);
            assertEquals(detail, after);
            assertArrayEquals(original, bytes(env, "restart"));
            assertEquals(before, env.bean(KnowledgeMapper.class).findDocument("restart"));
            assertEquals(0, observed.embeds.get());
            verifyVector(after, original, storedVersion(env, "restart"));
        } finally { control = null; }
    }

    private String submit(B4QaEnvironment env, String id, String text) throws Exception {
        String body = env.bean(ObjectMapper.class).writeValueAsString(Map.of("documentId", id, "text", text));
        String task = request(env, "POST", "/api/documents", body, 202).get("taskId").asString();
        until(() -> "SUCCEEDED".equals(env.bean(KnowledgeMapper.class).findTask(task).status()));
        return task;
    }

    private JsonNode get(B4QaEnvironment env, String path, int status) throws Exception {
        return request(env, "GET", path, null, status);
    }

    private JsonNode request(B4QaEnvironment env, String method, String path, String body, int status) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + path))
                .timeout(Duration.ofSeconds(10));
        if (body != null) { builder.header("Content-Type", "application/json"); }
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(status, response.statusCode(), response.body());
        return env.bean(ObjectMapper.class).readTree(response.body());
    }

    /** 以独立BIG_ENDIAN编码校验HTTP数值精度；不以展示层自己的SHA算法作预期。 */
    private static void verifyVector(JsonNode detail, byte[] expected, String version) throws Exception {
        assertTrue(detail.get("vectorReady").asBoolean());
        var info = detail.get("vectorInfo");
        assertEquals(6, info.size());
        assertEquals(version, info.get("embeddingVersion").asString());
        assertEquals(256, info.get("dimensions").asInt());
        assertEquals(1024, info.get("byteLength").asInt());
        assertEquals(256, info.get("values").size());
        assertEquals(sha(expected), info.get("sha256").asString());
        ByteBuffer actual = ByteBuffer.allocate(1024).order(ByteOrder.BIG_ENDIAN);
        for (JsonNode value : info.get("values")) {
            float number = (float) value.asDouble();
            assertTrue(Float.isFinite(number));
            actual.putInt(Float.floatToRawIntBits(number));
        }
        assertArrayEquals(expected, actual.array(), "全部256维与真实存储原始位一致，包含负零");
    }

    private static String sha(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static byte[] bytes(B4QaEnvironment env, String id) throws Exception {
        try (var connection = env.bean(DataSource.class).getConnection();
             var statement = connection.prepareStatement("SELECT vector_bytes FROM documents WHERE document_id=?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getBytes(1); }
        }
    }

    private static String storedVersion(B4QaEnvironment env, String id) throws Exception {
        try (var connection = env.bean(DataSource.class).getConnection();
             var statement = connection.prepareStatement("SELECT embedding_version FROM documents WHERE document_id=?")) {
            statement.setString(1, id);
            try (var rows = statement.executeQuery()) { assertTrue(rows.next()); return rows.getString(1); }
        }
    }

    static final class Control {
        final AtomicInteger embeds = new AtomicInteger();
        final AtomicInteger detailQueries = new AtomicInteger();
        final AtomicBoolean blockNext = new AtomicBoolean();
        final CountDownLatch snapshotRead = new CountDownLatch(1);
        final CountDownLatch release = new CountDownLatch(1);
    }

    @TestConfiguration(proxyBeanMethods = false)
    @ConditionalOnProperty(name = "qa.vector-info-hooks", havingValue = "true")
    static class Hooks {
        @Bean
        static BeanPostProcessor countComputations() {
            Control observed = control;
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String name) {
                    if (bean instanceof TextEmbedder original) {
                        return new TextEmbedder() {
                            @Override public float[] embed(String text) {
                                observed.embeds.incrementAndGet();
                                return original.embed(text);
                            }
                            @Override public String version() { return original.version(); }
                        };
                    }
                    return bean;
                }
            };
        }

        @Bean
        Interceptor countDetailQueries() { return new SnapshotHook(control); }
    }

    /** 在真实SQL读取结束后阻塞响应，确保并发提交发生在明确边界而非依靠sleep。 */
    @Intercepts(@Signature(type = Executor.class, method = "query", args = {
            MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}))
    static final class SnapshotHook implements Interceptor {
        private final Control observed;
        SnapshotHook(Control observed) { this.observed = observed; }
        @Override
        public Object intercept(Invocation invocation) throws Throwable {
            MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
            boolean detail = statement.getId().endsWith("DocumentDetailMapper.findDetail");
            if (detail) { observed.detailQueries.incrementAndGet(); }
            Object result = invocation.proceed();
            if (detail && observed.blockNext.compareAndSet(true, false)) {
                observed.snapshotRead.countDown();
                await(observed.release);
            }
            return result;
        }
    }
}
