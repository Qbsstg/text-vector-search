package dev.twelvetimes.vector.unit;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.document.DocumentViews;
import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.DocumentDetailMapper;
import dev.twelvetimes.vector.persistence.DocumentDetailRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskQueryService;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.Proxy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 详情展示以已保存字节为事实来源，不让 GET 借展示触发计算或写入。 */
class DocumentVectorInfoTest {
    private static final Instant COMPLETED = Instant.parse("2026-10-27T00:00:00Z");
    private static final String ZERO_SHA256 = "5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef";
    @TempDir Path directory;

    @Test
    void 非零向量匹配既有独立黄金摘要且完整保留float位() {
        float[] vector = new CharNgramTextEmbedder(32, 20000).embed("ababa");
        var detail = DocumentViews.detail(row("SUCCEEDED", "历史版本", VectorCodec.encode(vector), true));
        var info = detail.vectorInfo();
        assertTrue(detail.vectorReady());
        assertTrue(detail.invalidated(), "失效与向量就绪正交");
        assertEquals("历史版本", info.embeddingVersion());
        assertEquals(256, info.dimensions());
        assertEquals(1024, info.byteLength());
        assertEquals("ad82c864c2dec3476778eeaf5f6e83a64d5827b9f029f22737e7e33523125ad1", info.sha256());
        assertFalse(info.zeroVector());
        assertEquals(256, info.values().size());
        for (int index = 0; index < vector.length; index++) {
            assertEquals(Float.floatToRawIntBits(vector[index]), Float.floatToRawIntBits(info.values().get(index)));
        }
        assertThrows(UnsupportedOperationException.class, () -> info.values().set(0, 1.0f));
    }

    @Test
    void 空文零向量成功后仍就绪且摘要使用实际1024字节() {
        var detail = DocumentViews.detail(new DocumentDetailRow("empty", "", "default", COMPLETED,
                false, null, COMPLETED, 0, "task", "SUCCEEDED", "empty-v1", new byte[1024]));
        assertTrue(detail.vectorReady());
        assertTrue(detail.vectorInfo().zeroVector());
        assertEquals(ZERO_SHA256, detail.vectorInfo().sha256());
        assertTrue(detail.vectorInfo().values().stream().allMatch(value -> value == 0.0f));
    }

    @Test
    void 负零数值上视为零但值与原始字节指纹保留符号位() {
        byte[] bytes = new byte[1024];
        bytes[0] = (byte) 0x80;
        var info = DocumentViews.detail(row("SUCCEEDED", "negative-zero", bytes, false)).vectorInfo();
        assertTrue(info.zeroVector());
        assertEquals(0x80000000, Float.floatToRawIntBits(info.values().get(0)));
        assertEquals("8b3d85885d0da8c64ceff3a0b03462b7c2853ff7bf61e406ebd61d34dab0d2bb", info.sha256());
    }

    @Test
    void 未完成失败或成功但无字节均不以零向量占位() {
        for (String status : List.of("QUEUED", "RUNNING", "FAILED", "SUCCEEDED")) {
            var detail = DocumentViews.detail(row(status, null, null, false));
            assertFalse(detail.vectorReady(), status);
            assertNull(detail.vectorInfo(), status);
        }
        for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
            assertNull(DocumentViews.detail(row(status, "ignored", new byte[1], false)).vectorInfo(), status);
        }
    }

    @Test
    void 长度损坏NaN与正负无穷均拒绝伪造成功详情() {
        for (byte[] bytes : List.of(new byte[0], new byte[1023], new byte[1025])) {
            assertThrows(IllegalStateException.class,
                    () -> DocumentViews.detail(row("SUCCEEDED", "bad", bytes, false)));
        }
        for (int bits : new int[] {0x7fc00000, 0x7f800000, 0xff800000}) {
            byte[] bytes = new byte[1024];
            ByteBuffer.wrap(bytes).putInt(4 * 255, bits);
            assertThrows(IllegalStateException.class,
                    () -> DocumentViews.detail(row("SUCCEEDED", "bad", bytes, false)));
        }
    }

    @Test
    void 缺失版本或完成时间不制造可用向量元信息() {
        for (String version : new String[] {null, "", " "}) {
            assertThrows(IllegalStateException.class,
                    () -> DocumentViews.detail(row("SUCCEEDED", version, new byte[1024], false)));
        }
        var incomplete = new DocumentDetailRow("doc", "文本", "default", COMPLETED, false, null,
                null, 0, "task", "SUCCEEDED", "old", new byte[1024]);
        assertThrows(IllegalStateException.class, () -> DocumentViews.detail(incomplete));
    }

    @Test
    void 详情只调用专用读取且参数非法与不存在保持原错误() {
        KnowledgeMapper forbidden = (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                new Class<?>[] {KnowledgeMapper.class}, (proxy, method, args) -> {
                    throw new AssertionError("详情不应访问轻量查询或写入方法：" + method.getName());
                });
        AtomicInteger reads = new AtomicInteger();
        DocumentDetailMapper mapper = id -> {
            reads.incrementAndGet();
            return "doc".equals(id) ? row("SUCCEEDED", "saved-v1", new byte[1024], false) : null;
        };
        var service = new DocumentQueryService(forbidden, mapper,
                new RequestValidator(B2TestSupport.properties(20000)), new ForbiddenTransactionManager(),
                new TaskReadiness(forbidden));
        assertEquals(ZERO_SHA256, service.detail("doc").vectorInfo().sha256());
        assertEquals(1, reads.get());
        assertEquals(400, assertThrows(BusinessException.class, () -> service.detail("bad/id")).status());
        assertEquals(1, reads.get(), "参数失败在 SQL 前拒绝");
        assertEquals("DOCUMENT_NOT_FOUND", assertThrows(BusinessException.class, () -> service.detail("missing")).code());
        assertEquals(2, reads.get());
    }

    @Test
    void 真实SQL按文档版本展示持久化字节且重复读取不写计数状态或向量() {
        try (var context = B2TestSupport.start(directory.resolve("read-only"))) {
            var submission = context.getBean(DocumentAdmissionService.class)
                    .submit(new SubmitDocumentRequest("doc", "此原文的向量非零，保存值却是零", null));
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            byte[] bytes = new byte[1024];
            jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version='saved-old-version',"
                    + "vector_completed_at=?,hit_count=9007199254740993,invalidated=TRUE,invalidated_at=? WHERE document_id='doc'",
                    bytes, COMPLETED, COMPLETED);
            jdbc.update("UPDATE embedding_tasks SET status='SUCCEEDED',embedding_version='different-task-version' WHERE document_id='doc'");
            var mapper = context.getBean(KnowledgeMapper.class);
            var before = mapper.findDocument("doc");
            var service = context.getBean(DocumentQueryService.class);
            for (int index = 0; index < 3; index++) {
                var detail = service.detail("doc");
                assertEquals("saved-old-version", detail.vectorInfo().embeddingVersion());
                assertEquals(ZERO_SHA256, detail.vectorInfo().sha256());
                assertTrue(detail.invalidated());
                assertEquals("9007199254740993", detail.hitCount());
            }
            assertEquals(before, mapper.findDocument("doc"));
            assertArrayEquals(bytes, jdbc.queryForObject("SELECT vector_bytes FROM documents WHERE document_id='doc'", byte[].class));
            assertNull(context.getBean(TaskQueryService.class).get(submission.taskId()).document().vectorInfo(),
                    "任务轮询保持原有轻量详情");
            assertTrue(service.list(0, 20, null, null).items().get(0).vectorReady());
        }
    }

    @Test
    void HTTP坏向量返回500且不泄露字节或改变任务就绪状态() throws Exception {
        try (var context = new SpringApplication(B2CompositionTestApplication.class).run("--server.port=0",
                "--server.address=127.0.0.1", "--spring.datasource.url=jdbc:h2:file:"
                        + directory.resolve("http").toAbsolutePath() + "/knowledge;WRITE_DELAY=0",
                "--logging.level.root=ERROR", "--spring.main.banner-mode=off")) {
            context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("broken", "原文", null));
            byte[] bytes = new byte[1024];
            ByteBuffer.wrap(bytes).putInt(0x7fc00000);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version='saved',vector_completed_at=? WHERE document_id='broken'",
                    bytes, COMPLETED);
            jdbc.update("UPDATE embedding_tasks SET status='SUCCEEDED' WHERE document_id='broken'");
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            var response = HttpClient.newHttpClient().send(HttpRequest.newBuilder(
                    URI.create("http://127.0.0.1:" + port + "/api/documents/broken"))
                    .timeout(Duration.ofSeconds(5)).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertEquals(500, response.statusCode());
            assertTrue(response.body().contains("INTERNAL_ERROR"));
            assertFalse(response.body().contains("vectorInfo"));
            assertFalse(response.body().contains("NaN"));
            assertTrue(context.getBean(TaskReadiness.class).accepting());
            assertEquals("SUCCEEDED", context.getBean(KnowledgeMapper.class).findDocument("broken").status());
        }
    }

    private static DocumentDetailRow row(String status, String version, byte[] bytes, boolean invalidated) {
        return new DocumentDetailRow("doc", "原文", "default", COMPLETED, invalidated,
                invalidated ? COMPLETED : null, bytes == null ? null : COMPLETED, 7, "task", status, version, bytes);
    }

    /** 只读详情不需要显式写事务；任何进入事务、提交或回滚都视为回归。 */
    private static final class ForbiddenTransactionManager implements PlatformTransactionManager {
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { throw new AssertionError("不得启动写事务"); }
        @Override public void commit(TransactionStatus status) { throw new AssertionError("不得提交事务"); }
        @Override public void rollback(TransactionStatus status) { throw new AssertionError("不得回滚事务"); }
    }
}
