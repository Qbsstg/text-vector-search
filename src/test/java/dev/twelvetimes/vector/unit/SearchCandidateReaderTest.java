package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.SearchCandidate;
import dev.twelvetimes.vector.search.SearchCandidateReader;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchCandidateReaderTest {
    @TempDir Path directory;

    @Test
    void 单次SQL先过滤成功就绪有效双版本及精确渠道() {
        try (var context = B2TestSupport.start(directory)) {
            var admission = context.getBean(DocumentAdmissionService.class);
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            String version = context.getBean(TextEmbedder.class).version();
            for (String id : List.of("eligible", "other", "queued", "running", "failed", "invalid", "no-vector", "old-doc", "old-task")) {
                admission.submit(new SubmitDocumentRequest(id, "原文", id.equals("other") ? "faq" : "FAQ"));
            }
            jdbc.update("UPDATE embedding_tasks SET status='SUCCEEDED'");
            jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version=?,vector_completed_at=CURRENT_TIMESTAMP", new byte[1024], version);
            for (String status : List.of("QUEUED", "RUNNING", "FAILED")) {
                jdbc.update("UPDATE embedding_tasks SET status=? WHERE document_id=?", status, status.toLowerCase());
            }
            jdbc.update("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP WHERE document_id='invalid'");
            jdbc.update("UPDATE documents SET vector_bytes=NULL,embedding_version=NULL,vector_completed_at=NULL WHERE document_id='no-vector'");
            jdbc.update("UPDATE documents SET embedding_version='old' WHERE document_id='old-doc'");
            jdbc.update("UPDATE embedding_tasks SET embedding_version='old' WHERE document_id='old-task'");
            var reader = context.getBean(SearchCandidateReader.class);
            assertEquals(List.of("eligible"), reader.load(version, "FAQ").stream().map(SearchCandidate::documentId).toList());
            assertEquals(List.of("eligible", "other"), reader.load(version, null).stream().map(SearchCandidate::documentId).toList());
            assertTrue(reader.load(version, "missing").isEmpty());
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.hasResource(context.getBean(DataSource.class)));
        }
    }

    @Test
    void 候选仅装载有界前缀且向量快照不可被调用方修改() {
        try (var context = B2TestSupport.start(directory)) {
            context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest("long", "🙂".repeat(1000), null));
            var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
            String version = context.getBean(TextEmbedder.class).version();
            jdbc.update("UPDATE embedding_tasks SET status='SUCCEEDED'");
            jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version=?,vector_completed_at=CURRENT_TIMESTAMP", new byte[1024], version);
            SearchCandidate candidate = context.getBean(SearchCandidateReader.class).load(version, null).get(0);
            assertTrue(candidate.prefix().length() <= 800, "SQL前缀即使按code point计也不超过400个补充字符");
            assertTrue(candidate.prefix().codePointCount(0, candidate.prefix().length()) >= 200);
            candidate.vectorBytes()[0] = 1;
            assertEquals(0, candidate.vectorBytes()[0]);
        }
    }

    @Test
    void 超过文档容量明确失败而不是返回截断候选() {
        try (var context = B2TestSupport.start(directory)) {
            var admission = context.getBean(DocumentAdmissionService.class);
            admission.submit(new SubmitDocumentRequest("a", "", null));
            admission.submit(new SubmitDocumentRequest("b", "", null));
            var props = new VectorProperties(1, 1, 10, 1, 1, 20000, 262144, 100, 100, 32, 500, 32, 1);
            var reader = new SearchCandidateReader(context.getBean(SearchMapper.class), context.getBean(TaskReadiness.class), props,
                    context.getBean(PlatformTransactionManager.class));
            assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                    () -> reader.load(context.getBean(TextEmbedder.class).version(), null)).code());
        }
    }

    @Test
    void 停止或外层事务下读取拒绝且不绕过共享屏障() {
        try (var context = B2TestSupport.start(directory)) {
            var reader = context.getBean(SearchCandidateReader.class);
            String version = context.getBean(TextEmbedder.class).version();
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                    assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> reader.load(version, null)).code()));
            context.getBean(TaskReadiness.class).stopping();
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> reader.load(version, null)).code());
        }
    }
}
