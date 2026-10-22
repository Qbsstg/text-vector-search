package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskExecutionHandler;
import java.net.Socket;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 真HTTP验收；人工终态仅验证查询映射，不当作B3执行证据。 */
class B2HttpIT {
    @TempDir Path directory;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .version(HttpClient.Version.HTTP_1_1).build();

    @Test
    void 真实提交渠道默认与空文及无处理器不领取() throws Exception {
        try (var env = new B2QaEnvironment(directory, true)) {
            assertTrue(env.context.getBeansOfType(TaskExecutionHandler.class).isEmpty());
            for (String body : List.of("{\"documentId\":\"empty\",\"text\":\"\"}",
                    "{\"documentId\":\"null-channel\",\"text\":\"  原文  \",\"channel\":null}")) {
                var accepted = json(env, send(env, "POST", "/api/documents", body, 202));
                UUID.fromString(accepted.get("taskId").asString());
                assertEquals("QUEUED", accepted.get("status").asString());
            }
            var dispatcher = env.bean(TaskDispatcher.class);
            for (int tick = 0; tick < 5; tick++) {
                assertEquals(0, dispatcher.scanOnce());
            }
            var empty = env.bean(KnowledgeMapper.class).findDocument("empty");
            assertEquals("", empty.text());
            assertEquals("default", empty.channel());
            assertEquals("QUEUED", empty.status());
            assertEquals(0, empty.attemptCount());
            assertEquals(null, empty.runToken());
            assertEquals("  原文  ", env.bean(KnowledgeMapper.class).findDocument("null-channel").text());
            String channel = "😀".repeat(64);
            String body = env.bean(ObjectMapper.class).writeValueAsString(java.util.Map.of(
                    "documentId", "channel64", "text", "文", "channel", "\u2007" + channel + "\u00a0"));
            send(env, "POST", "/api/documents", body, 202);
            assertEquals(channel, env.bean(KnowledgeMapper.class).findDocument("channel64").channel());
            var all = json(env, send(env, "GET", "/api/documents", null, 200));
            assertEquals(3, all.get("totalElements").asInt());
            String encoded = URLEncoder.encode(channel, StandardCharsets.UTF_8);
            assertEquals(1, json(env, send(env, "GET", "/api/documents?channel=" + encoded, null, 200))
                    .get("totalElements").asInt());
            var duplicate = json(env, send(env, "POST", "/api/documents", "{\"documentId\":\"empty\",\"text\":\"覆盖\"}", 409));
            assertEquals("DOCUMENT_EXISTS", duplicate.get("code").asString());
            assertEquals("", env.bean(KnowledgeMapper.class).findDocument("empty").text());
        }
    }

    @Test
    void JSON类型空白分页与来源错误保持统一结构且不落库() throws Exception {
        try (var env = new B2QaEnvironment(directory, true)) {
            for (String body : List.of("{}", "{\"documentId\":\"bad\",\"text\":null}",
                    "{\"documentId\":9,\"text\":\"\"}", "{\"documentId\":\"bad\",\"text\":4}",
                    "{\"documentId\":\"bad\",\"text\":\"\",\"channel\":3}",
                    "{\"documentId\":\"bad\",\"text\":\"\",\"channel\":\" \\u2007\"}",
                    "{\"documentId\":\"bad\",\"text\":\"\\ud800\"}", "[]", "{}{}", "{")) {
                assertError(env, send(env, "POST", "/api/documents", body, 400));
            }
            for (String query : List.of("page=-1", "page=2147483648", "size=0", "size=101", "size=1.5",
                    "page=0&page=1", "invalidated=TRUE", "channel=%20")) {
                assertError(env, send(env, "GET", "/api/documents?" + query, null, 400));
            }
            send(env, "GET", "/api/documents/missing", null, 404);
            send(env, "GET", "/api/tasks/" + UUID.randomUUID(), null, 404);
            send(env, "GET", "/api/tasks/not-a-uuid", null, 400);
            String input = "{\"documentId\":\"origin\",\"text\":\"\"}";
            assertError(env, send(env, "POST", "/api/documents", input, 403,
                    "Origin", "http://other.example"));
            assertError(env, send(env, "POST", "/api/documents", input, 400,
                    "Content-Type", "text/plain"));
            assertEquals(0, env.bean(KnowledgeMapper.class).countDocuments());
            send(env, "POST", "/api/documents", input, 202, "Origin", "http://127.0.0.1:" + env.port());
            send(env, "PUT", "/api/documents/origin/invalidation", "{}", 400);
            assertFalse(env.bean(KnowledgeMapper.class).findDocument("origin").invalidated());
        }
    }

    @Test
    void 默认正文上限按实际chunked字节判断含边界且超限不落库() throws Exception {
        try (var env = new B2QaEnvironment(directory, true)) {
            int limit = 262144;
            assertEquals(202, chunked(env, padded("under", limit - 1)));
            assertEquals(202, chunked(env, padded("exact", limit)));
            assertEquals(413, chunked(env, padded("over", limit + 1)));
            // 畸形JSON的实际正文也必须先受字节限制，不能绕过过滤器。
            assertEquals(413, chunked(env, "{" + " ".repeat(limit)));
            assertError(env, send(env, "POST", "/api/documents", padded("length-over", limit + 1), 413));
            assertEquals(2, env.bean(KnowledgeMapper.class).countDocuments());
            assertEquals(2, env.bean(KnowledgeMapper.class).countPending());
        }
    }

    @Test
    void 四态查询字符串大计数排序与失效幂等保持独立() throws Exception {
        try (var env = new B2QaEnvironment(directory, true)) {
            for (String state : List.of("QUEUED", "RUNNING", "SUCCEEDED", "FAILED")) {
                String id = state.toLowerCase(java.util.Locale.ROOT);
                env.submit(id);
                env.sql("UPDATE embedding_tasks SET status=?,error_code=?,error_message=? WHERE document_id=?", state,
                        state.equals("FAILED") ? "TEST_FAILURE" : null, state.equals("FAILED") ? "安全原因" : null, id);
                env.sql("UPDATE documents SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:00+00',"
                        + "hit_count=9007199254740993 WHERE document_id=?", id);
                if (state.equals("SUCCEEDED")) {
                    env.sql("UPDATE documents SET vector_bytes=?,embedding_version='fixture',"
                            + "vector_completed_at=CURRENT_TIMESTAMP WHERE document_id=?", new byte[1024], id);
                }
                var taskId = env.bean(KnowledgeMapper.class).findDocument(id).taskId();
                var task = json(env, send(env, "GET", "/api/tasks/" + taskId, null, 200));
                assertEquals(state, task.get("status").asString());
                assertEquals(!state.equals("SUCCEEDED"), task.get("document").isNull());
                var detail = json(env, send(env, "GET", "/api/documents/" + id, null, 200));
                assertTrue(detail.get("hitCount").isString());
                assertEquals("9007199254740993", detail.get("hitCount").asString());
                assertEquals(state.equals("SUCCEEDED"), detail.get("vectorReady").asBoolean());
                assertEquals(Instant.parse("2026-10-22T00:00:00Z"), Instant.parse(detail.get("submittedAt").asString()));
            }
            var page = json(env, send(env, "GET", "/api/documents?page=0&size=2", null, 200));
            assertEquals(4, page.get("totalElements").asInt());
            assertEquals("failed", page.get("items").get(0).get("documentId").asString());
            assertEquals("queued", page.get("items").get(1).get("documentId").asString());
            var first = json(env, send(env, "PUT", "/api/documents/succeeded/invalidation", null, 200));
            var second = json(env, send(env, "PUT", "/api/documents/succeeded/invalidation", null, 200));
            assertEquals(first.get("invalidatedAt"), second.get("invalidatedAt"));
            var detail = json(env, send(env, "GET", "/api/documents/succeeded", null, 200));
            assertTrue(detail.get("vectorReady").asBoolean());
            assertTrue(detail.get("invalidated").asBoolean());
            assertEquals("SUCCEEDED", detail.get("taskStatus").asString());
            assertEquals("9007199254740993", detail.get("hitCount").asString());
            assertEquals(1, json(env, send(env, "GET", "/api/documents?invalidated=true", null, 200))
                    .get("totalElements").asInt());
            for (String id : List.of("queued", "running", "succeeded", "failed")) {
                assertEquals(9007199254740993L, env.bean(KnowledgeMapper.class).findDocument(id).hitCount());
            }
        }
    }

    @Test
    void 规范化扩张上限与补充码点通过真实HTTP校验() throws Exception {
        try (var env = new B2QaEnvironment(directory, true, "--vector.max-text-code-points=2")) {
            var mapper = env.bean(ObjectMapper.class);
            send(env, "POST", "/api/documents", mapper.writeValueAsString(java.util.Map.of(
                    "documentId", "emoji", "text", "😀😀")), 202);
            for (String text : List.of("😀😀😀", "ﬃ", "   ")) {
                assertError(env, send(env, "POST", "/api/documents", mapper.writeValueAsString(java.util.Map.of(
                        "documentId", "bad", "text", text)), 400));
            }
            assertEquals(1, env.bean(KnowledgeMapper.class).countDocuments());
        }
    }

    private HttpResponse<String> send(B2QaEnvironment env, String method, String path, String body, int expected,
                                      String... headers) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + env.port() + path))
                .timeout(Duration.ofSeconds(8));
        boolean customType = false;
        for (int index = 0; index < headers.length; index += 2) {
            builder.header(headers[index], headers[index + 1]);
            customType |= headers[index].equalsIgnoreCase("Content-Type");
        }
        if (!customType && body != null) {
            builder.header("Content-Type", "application/json");
        }
        var response = client.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(expected, response.statusCode(), response.body());
        assertTrue(response.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
        return response;
    }

    private static JsonNode json(B2QaEnvironment env, HttpResponse<String> response) {
        return env.bean(ObjectMapper.class).readTree(response.body());
    }

    private static void assertError(B2QaEnvironment env, HttpResponse<String> response) {
        JsonNode error = json(env, response);
        assertTrue(error.get("code").isString());
        assertTrue(error.get("message").isString());
        assertTrue(error.get("fieldErrors").isArray());
        assertFalse(error.has("stackTrace"));
        assertFalse(error.has("sql"));
    }

    private static String padded(String id, int bytes) {
        String json = "{\"documentId\":\"" + id + "\",\"text\":\"\"}";
        return json + " ".repeat(bytes - json.length());
    }

    /** 手写HTTP/1.1线报文，明确无Content-Length，不依赖客户端自动挑选传输编码。 */
    private static int chunked(B2QaEnvironment env, String body) throws Exception {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        try (var socket = new Socket("127.0.0.1", env.port())) {
            socket.setSoTimeout(8000);
            var output = socket.getOutputStream();
            output.write(("POST /api/documents HTTP/1.1\r\nHost: 127.0.0.1:" + env.port()
                    + "\r\nContent-Type: application/json\r\nTransfer-Encoding: chunked\r\nConnection: close\r\n\r\n")
                    .getBytes(StandardCharsets.US_ASCII));
            for (int offset = 0; offset < bytes.length; offset += 4096) {
                int count = Math.min(4096, bytes.length - offset);
                output.write((Integer.toHexString(count) + "\r\n").getBytes(StandardCharsets.US_ASCII));
                output.write(bytes, offset, count);
                output.write("\r\n".getBytes(StandardCharsets.US_ASCII));
            }
            output.write("0\r\n\r\n".getBytes(StandardCharsets.US_ASCII));
            output.flush();
            String response = new String(socket.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertTrue(response.startsWith("HTTP/1.1 "), response);
            return Integer.parseInt(response.substring(9, 12));
        }
    }
}
