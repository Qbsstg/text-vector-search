package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskQueryService;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentManagementApiTest {
    @TempDir Path directory;

    @Test
    void 五管理接口真实闭环且GET不增加计数() throws Exception {
        try (var context = web("flow")) {
            var created = send(context, "POST", "/api/documents", "{\"documentId\":\"doc\",\"text\":\"\"}", null);
            assertEquals(202, created.statusCode());
            String taskId = tree(context, created).get("taskId").asString();
            var task = tree(context, send(context, "GET", "/api/tasks/" + taskId, null, null));
            assertEquals("QUEUED", task.get("status").asString());
            assertTrue(task.get("document").isNull());
            var detail = tree(context, send(context, "GET", "/api/documents/doc", null, null));
            assertEquals("default", detail.get("channel").asString());
            assertEquals("0", detail.get("hitCount").asString());
            assertFalse(detail.get("vectorReady").asBoolean());
            var page = tree(context, send(context, "GET", "/api/documents?page=0&size=1", null, null));
            assertEquals(1, page.get("totalElements").asInt());
            assertEquals("doc", page.get("items").get(0).get("documentId").asString());
            var invalidated = tree(context, send(context, "PUT", "/api/documents/doc/invalidation", null, null));
            var repeated = tree(context, send(context, "PUT", "/api/documents/doc/invalidation", null, null));
            assertEquals(invalidated, repeated);
            assertEquals(0, context.getBean(KnowledgeMapper.class).findDocument("doc").hitCount());
            assertEquals(409, send(context, "POST", "/api/documents", "{\"documentId\":\"doc\",\"text\":\"new\"}", null).statusCode());
            assertEquals(404, send(context, "GET", "/api/documents/missing", null, null).statusCode());
        }
    }

    @Test
    void HTTP空值类型非法JSON分页与显式空渠道统一400() throws Exception {
        try (var context = web("invalid")) {
            for (String body : List.of("", "{", "{}", "[]", "null",
                    "{\"documentId\":1,\"text\":\"\"}", "{\"documentId\":\"a\",\"text\":null}",
                    "{\"documentId\":\"a\",\"text\":42}", "{\"documentId\":\"a\",\"text\":\"\",\"channel\":\" \"}",
                    "{\"documentId\":\"a\",\"text\":\"\"} {}")) {
                var response = send(context, "POST", "/api/documents", body, null);
                assertEquals(400, response.statusCode(), body);
                assertTrue(tree(context, response).get("fieldErrors").isArray());
                assertFalse(response.body().contains("SELECT"));
            }
            for (String query : List.of("?page=-1", "?size=101", "?page=2147483648", "?invalidated=TRUE", "?channel=", "?page=0&page=1")) {
                assertEquals(400, send(context, "GET", "/api/documents" + query, null, null).statusCode(), query);
            }
            assertEquals(400, send(context, "PUT", "/api/documents/a/invalidation", "{}", null).statusCode());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 正文真实字节限制覆盖chunked且来源校验不开放CORS() throws Exception {
        try (var context = web("boundary")) {
            String body = "{\"documentId\":\"large\",\"text\":\"" + "x".repeat(600) + "\"}";
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            var publisher = HttpRequest.BodyPublishers.ofInputStream(() -> new ByteArrayInputStream(bytes));
            assertEquals(-1, publisher.contentLength(), "无Content-Length的发布器触发真实分块传输");
            var request = HttpRequest.newBuilder(URI.create(base(context) + "/api/documents"))
                    .header("Content-Type", "application/json").POST(publisher).build();
            var response = client().send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(413, response.statusCode());
            assertEquals("REQUEST_TOO_LARGE", tree(context, response).get("code").asString());
            String small = "{\"documentId\":\"ok\",\"text\":\"\"}";
            assertEquals(403, send(context, "POST", "/api/documents", small, "https://evil.example").statusCode());
            assertEquals(403, send(context, "POST", "/api/documents", small, "null").statusCode());
            assertEquals(403, send(context, "POST", "/api/documents", small, "//127.0.0.1").statusCode());
            assertEquals(403, send(context, "POST", "/api/documents", small, "mailto:user@example.com").statusCode());
            var allowed = send(context, "POST", "/api/documents", small, base(context));
            assertEquals(202, allowed.statusCode());
            assertTrue(allowed.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
            for (String type : List.of("text/plain", "*/*", "application/*")) {
                var wrongType = HttpRequest.newBuilder(URI.create(base(context) + "/api/documents"))
                        .header("Content-Type", type).POST(HttpRequest.BodyPublishers.ofString(small)).build();
                assertEquals(400, client().send(wrongType, HttpResponse.BodyHandlers.ofString()).statusCode());
            }
            var withCharset = HttpRequest.newBuilder(URI.create(base(context) + "/api/documents"))
                    .header("Content-Type", "application/json;charset=UTF-8").POST(HttpRequest.BodyPublishers.ofString(
                            "{\"documentId\":\"charset\",\"text\":\"\"}")).build();
            assertEquals(202, client().send(withCharset, HttpResponse.BodyHandlers.ofString()).statusCode());
        }
    }

    @Test
    void 四态详情筛选排序与失效正交及大整数计数映射() {
        try (var context = B2TestSupport.start(directory.resolve("mapping"))) {
            var admission = context.getBean(DocumentAdmissionService.class);
            var query = context.getBean(DocumentQueryService.class);
            var tasks = context.getBean(TaskQueryService.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            for (String id : List.of("d", "c", "b", "a")) {
                admission.submit(new SubmitDocumentRequest(id, id, "FAQ"));
            }
            jdbc.update("UPDATE documents SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:00+00'");
            assertEquals(List.of("a", "b", "c", "d"), query.list(0, 20, "FAQ", false).items().stream()
                    .map(item -> item.documentId()).toList());
            assertEquals(0, query.list(0, 20, "faq", null).totalElements());
            for (int index = 0; index < 4; index++) {
                String id = List.of("a", "b", "c", "d").get(index);
                TaskStatus status = TaskStatus.values()[index];
                jdbc.update("UPDATE embedding_tasks SET status=? WHERE document_id=?", status.name(), id);
                var task = tasks.get(query.detail(id).taskId());
                assertEquals(status, task.status());
                if (status == TaskStatus.SUCCEEDED) { assertNotNull(task.document()); }
                else { assertNull(task.document()); }
            }
            jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version='test',vector_completed_at=CURRENT_TIMESTAMP,hit_count=9007199254740993 WHERE document_id='c'", new byte[1024]);
            var first = query.invalidate("c");
            assertEquals(first, query.invalidate("c"));
            assertTrue(query.detail("c").vectorReady());
            assertTrue(query.detail("c").invalidated());
            assertEquals("9007199254740993", query.detail("c").hitCount());
            assertEquals(1, query.list(0, 20, null, true).totalElements());
        }
    }

    private ConfigurableApplicationContext web(String name) {
        return new SpringApplication(VectorSearchApplication.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve(name).toAbsolutePath() + "/knowledge;WRITE_DELAY=0",
                "--vector.max-request-bytes=512", "--logging.level.root=ERROR", "--spring.main.banner-mode=off");
    }

    private static String base(ConfigurableApplicationContext context) {
        int port = ((WebServerApplicationContext) context).getWebServer().getPort();
        return "http://127.0.0.1:" + port;
    }

    private static HttpClient client() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); }

    private static HttpResponse<String> send(ConfigurableApplicationContext context, String method,
                                             String path, String body, String origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base(context) + path)).timeout(Duration.ofSeconds(5));
        if (origin != null) { builder.header("Origin", origin); }
        if (method.equals("POST")) { builder.header("Content-Type", "application/json"); }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client().send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private static JsonNode tree(ConfigurableApplicationContext context, HttpResponse<String> response) throws Exception {
        return context.getBean(ObjectMapper.class).readTree(response.body());
    }
}
