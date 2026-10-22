package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchControllerTest {
    @TempDir Path directory;

    @Test
    void 真实生产HTTP提交向量化搜索详情计数闭环() throws Exception {
        try (var context = web()) {
            String text = "押金退款🙂" + "🙂".repeat(210);
            create(context, "normal", text, "FAQ");
            create(context, "empty", "", "default");
            var response = post(context, "/api/search", json(context, Map.of("text", text, "topK", 1, "channel", "FAQ")));
            assertEquals(200, response.statusCode());
            var hits = tree(context, response).get("hits");
            assertEquals(1, hits.size());
            assertEquals("normal", hits.get(0).get("documentId").asString());
            assertEquals(1.0, hits.get(0).get("score").asDouble(), 1e-12);
            assertEquals(text.substring(0, text.offsetByCodePoints(0, 200)), hits.get(0).get("snippet").asString());
            var detail = get(context, "/api/documents/normal");
            assertEquals("1", tree(context, detail).get("hitCount").asString());
            get(context, "/api/documents/normal");
            get(context, "/api/documents");
            assertEquals(1, context.getBean(KnowledgeMapper.class).findDocument("normal").hitCount());
            var emptyQuery = tree(context, post(context, "/api/search", "{\"text\":\"\",\"topK\":100,\"channel\":null}"));
            assertEquals("empty", emptyQuery.get("hits").get(0).get("documentId").asString());
            assertEquals("normal", emptyQuery.get("hits").get(1).get("documentId").asString());
            assertEquals(0.0, emptyQuery.get("hits").get(0).get("score").asDouble());
            assertEquals(2, context.getBean(KnowledgeMapper.class).findDocument("normal").hitCount());
            assertEquals(1, context.getBean(KnowledgeMapper.class).findDocument("empty").hitCount());
            var noChannel = tree(context, post(context, "/api/search", "{\"text\":\"\",\"topK\":5,\"channel\":\"faq\"}"));
            assertTrue(noChannel.get("hits").isEmpty());
        }
    }

    @Test
    void HTTP严格类型与正文来源边界继续生效() throws Exception {
        try (var context = web()) {
            for (String invalid : new String[] {"{}", "[]", "{", "{\"text\":null,\"topK\":1}",
                    "{\"text\":\"\",\"topK\":1.0}", "{\"text\":\"\",\"topK\":\"1\"}",
                    "{\"text\":\"\",\"topK\":2147483648}", "{\"text\":\"\",\"topK\":1,\"channel\":\" \"}",
                    "{\"text\":\"\\ud800\",\"topK\":1}"}) {
                var response = post(context, "/api/search", invalid);
                assertEquals(400, response.statusCode(), invalid);
                assertTrue(tree(context, response).get("fieldErrors").isArray());
            }
            assertEquals(413, post(context, "/api/search", " ".repeat(262145)).statusCode());
            var foreign = HttpRequest.newBuilder(URI.create(base(context) + "/api/search"))
                    .header("Content-Type", "application/json").header("Origin", "https://evil.example")
                    .POST(HttpRequest.BodyPublishers.ofString("{\"text\":\"\",\"topK\":1}")).build();
            assertEquals(403, client().send(foreign, HttpResponse.BodyHandlers.ofString()).statusCode());
            assertEquals(200, post(context, "/api/search", "{\"text\":\"\",\"topK\":100}").statusCode());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 库中NaN候选不能伪装无命中或增加计数() throws Exception {
        try (var context = web()) {
            create(context, "bad", "真实B1原文", "default");
            byte[] bad = new byte[1024];
            ByteBuffer.wrap(bad).putFloat(Float.NaN);
            new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE documents SET vector_bytes=? WHERE document_id='bad'", bad);
            var response = post(context, "/api/search", "{\"text\":\"原文\",\"topK\":5}");
            assertEquals(503, response.statusCode());
            assertEquals("DATABASE_UNAVAILABLE", tree(context, response).get("code").asString());
            assertFalse(response.body().contains("SELECT"));
            assertEquals(0, context.getBean(KnowledgeMapper.class).findDocument("bad").hitCount());
        }
    }

    private ConfigurableApplicationContext web() {
        return new SpringApplication(VectorSearchApplication.class).run("--server.port=0", "--server.address=127.0.0.1",
                "--spring.datasource.url=jdbc:h2:file:" + directory.toAbsolutePath() + "/knowledge;WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE",
                "--logging.level.root=ERROR", "--spring.main.banner-mode=off");
    }

    private static void create(ConfigurableApplicationContext context, String id, String text, String channel) throws Exception {
        assertEquals(202, post(context, "/api/documents", json(context, Map.of("documentId", id, "text", text, "channel", channel))).statusCode());
        TaskRecoveryServiceTest.until(() -> "SUCCEEDED".equals(context.getBean(KnowledgeMapper.class).findDocument(id).status()));
    }

    private static String base(ConfigurableApplicationContext context) {
        return "http://127.0.0.1:" + ((WebServerApplicationContext) context).getWebServer().getPort();
    }

    private static HttpClient client() { return HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); }
    private static HttpResponse<String> post(ConfigurableApplicationContext context, String path, String body) throws Exception {
        return client().send(HttpRequest.newBuilder(URI.create(base(context) + path)).timeout(Duration.ofSeconds(8))
                .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }
    private static HttpResponse<String> get(ConfigurableApplicationContext context, String path) throws Exception {
        return client().send(HttpRequest.newBuilder(URI.create(base(context) + path)).timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
    }
    private static String json(ConfigurableApplicationContext context, Object value) throws Exception { return context.getBean(ObjectMapper.class).writeValueAsString(value); }
    private static JsonNode tree(ConfigurableApplicationContext context, HttpResponse<String> response) throws Exception { return context.getBean(ObjectMapper.class).readTree(response.body()); }
}
