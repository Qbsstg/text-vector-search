package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.SearchCandidate;
import dev.twelvetimes.vector.search.SearchCandidateReader;
import dev.twelvetimes.vector.search.SearchCandidateSource;
import dev.twelvetimes.vector.search.SearchHitCountService;
import dev.twelvetimes.vector.search.SearchHitCounter;
import dev.twelvetimes.vector.search.SearchService;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchServiceTest {
    @TempDir Path directory;

    @Test
    void 数学边界夹具保留负零分与同分顺序且仅最终命中计数() {
        try (var context = data()) {
            float[] positive = new float[256];
            positive[0] = 1;
            float[] negative = new float[256];
            negative[0] = -1;
            String prefix = "🙂".repeat(199) + "中🙂";
            SearchCandidateSource source = (version, channel) -> List.of(
                    new SearchCandidate("c", "default", VectorCodec.encode(negative), prefix),
                    new SearchCandidate("b", "default", new byte[1024], prefix),
                    new SearchCandidate("a", "default", VectorCodec.encode(positive), prefix));
            var actual = context.getBean(TextEmbedder.class);
            TextEmbedder fixture = new TextEmbedder() {
                @Override public String version() { return actual.version(); }
                @Override public float[] embed(String text) { return positive.clone(); }
            };
            var service = service(context, fixture, source, context.getBean(SearchHitCountService.class));
            var response = service.search(new SearchRequest("数学夹具", 100, null));
            assertEquals(List.of("a", "b", "c"), response.hits().stream().map(hit -> hit.documentId()).toList());
            assertEquals(List.of(1.0, 0.0, -1.0), response.hits().stream().map(hit -> hit.score()).toList());
            assertEquals("🙂".repeat(199) + "中", response.hits().get(0).snippet());
            for (String id : List.of("a", "b", "c")) { assertEquals(1, count(context, id)); }
            var actualService = context.getBean(SearchService.class);
            var emptyQuery = actualService.search(new SearchRequest("", 2, null));
            assertEquals(List.of("a", "b"), emptyQuery.hits().stream().map(hit -> hit.documentId()).toList());
            assertEquals(2, count(context, "a"));
            assertEquals(2, count(context, "b"));
            assertEquals(1, count(context, "c"));
        }
    }

    @Test
    void 快照后失效仍返回并计数而新搜索排除() throws Exception {
        try (var context = data()) {
            CountDownLatch snapshotReady = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            SearchCandidateSource source = (version, channel) -> {
                var snapshot = context.getBean(SearchCandidateReader.class).load(version, channel);
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.hasResource(context.getBean(DataSource.class)));
                snapshotReady.countDown();
                await(release);
                return snapshot;
            };
            var service = service(context, context.getBean(TextEmbedder.class), source, context.getBean(SearchHitCountService.class));
            var executor = Executors.newSingleThreadExecutor();
            try {
                var work = executor.submit(() -> service.search(new SearchRequest("", 1, null)));
                assertTrue(snapshotReady.await(5, TimeUnit.SECONDS));
                var invalidation = context.getBean(DocumentQueryService.class).invalidate("a");
                release.countDown();
                assertEquals("a", work.get(5, TimeUnit.SECONDS).hits().get(0).documentId());
                assertEquals(1, count(context, "a"));
                assertEquals(invalidation.invalidatedAt(), context.getBean(DocumentQueryService.class).detail("a").invalidatedAt());
                assertEquals(List.of("b", "c"), context.getBean(SearchService.class).search(new SearchRequest("", 100, null))
                        .hits().stream().map(hit -> hit.documentId()).toList());
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test
    void 许可直到物理commit结束才释放且忙请求不增加计数() throws Exception {
        try (var context = data()) {
            CountDownLatch beforeCommit = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            var actualManager = context.getBean(PlatformTransactionManager.class);
            AtomicBoolean once = new AtomicBoolean(true);
            var manager = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actualManager.getTransaction(definition); }
                @Override public void rollback(TransactionStatus status) { actualManager.rollback(status); }
                @Override public void commit(TransactionStatus status) {
                    if (once.compareAndSet(true, false)) { beforeCommit.countDown(); await(release); }
                    actualManager.commit(status);
                }
            };
            var service = service(context, context.getBean(TextEmbedder.class), context.getBean(SearchCandidateReader.class),
                    new SearchHitCountService(context.getBean(SearchMapper.class), context.getBean(TaskReadiness.class), manager));
            var executor = Executors.newSingleThreadExecutor();
            try {
                var work = executor.submit(() -> service.search(new SearchRequest("", 1, null)));
                assertTrue(beforeCommit.await(5, TimeUnit.SECONDS));
                assertEquals("SEARCH_BUSY", assertThrows(BusinessException.class,
                        () -> service.search(new SearchRequest("", 1, null))).code());
                assertEquals(0, count(context, "a"));
                release.countDown();
                assertEquals(1, work.get(5, TimeUnit.SECONDS).hits().size());
                assertEquals(1, count(context, "a"));
                service.search(new SearchRequest("", 1, null));
                assertEquals(2, count(context, "a"));
            } finally { release.countDown(); executor.shutdownNow(); }
        }
    }

    @Test
    void 查询CPU不持事务连接或屏障且停止后返回不能访问SQL() throws Exception {
        var context = data();
        var readiness = context.getBean(TaskReadiness.class);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger sourceCalls = new AtomicInteger();
        TextEmbedder actual = context.getBean(TextEmbedder.class);
        TextEmbedder blocked = new TextEmbedder() {
            @Override public String version() { return actual.version(); }
            @Override public float[] embed(String text) {
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.hasResource(context.getBean(DataSource.class)));
                entered.countDown();
                await(release);
                return actual.embed(text);
            }
        };
        var realSource = context.getBean(SearchCandidateReader.class);
        var service = service(context, blocked, (version, channel) -> { sourceCalls.incrementAndGet(); return realSource.load(version, channel); },
                ids -> { throw new AssertionError("停止后不得计数"); });
        var executor = Executors.newSingleThreadExecutor();
        try {
            var work = executor.submit(() -> assertThrows(BusinessException.class, () -> service.search(new SearchRequest("真实B1", 1, null))).code());
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            context.close();
            assertFalse(readiness.writeBarrier().isOpen(), "CPU等待没有持有关闭许可，数据源可以正常销毁");
            release.countDown();
            assertEquals("SERVICE_NOT_READY", work.get(5, TimeUnit.SECONDS));
            assertEquals(1, sourceCalls.get(), "来源入口被调用，但真实来源必须在SQL之前拒绝");
        } finally { release.countDown(); executor.shutdownNow(); context.close(); }
    }

    @Test
    void 坏向量失败而非空命中并释放搜索许可() {
        try (var context = data()) {
            byte[] corrupt = new byte[1024];
            ByteBuffer.wrap(corrupt).putFloat(Float.NaN);
            AtomicBoolean first = new AtomicBoolean(true);
            SearchCandidateSource source = (version, channel) -> first.compareAndSet(true, false)
                    ? List.of(new SearchCandidate("a", "default", corrupt, "原文"))
                    : context.getBean(SearchCandidateReader.class).load(version, channel);
            var service = service(context, context.getBean(TextEmbedder.class), source, context.getBean(SearchHitCountService.class));
            assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                    () -> service.search(new SearchRequest("", 1, null))).code());
            assertEquals(0, count(context, "a"));
            assertEquals(1, service.search(new SearchRequest("", 1, null)).hits().size());
            assertEquals(1, count(context, "a"));
        }
    }

    @Test
    void 搜索入口拒绝外层事务并且空候选不调用计数服务() {
        try (var context = data()) {
            var service = service(context, context.getBean(TextEmbedder.class), (version, channel) -> List.of(),
                    ids -> { throw new AssertionError("空命中不制造写入"); });
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                    assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                            () -> service.search(new SearchRequest("", 1, null))).code()));
            assertTrue(service.search(new SearchRequest("", 100, null)).hits().isEmpty());
        }
    }

    private ConfigurableApplicationContext data() {
        var context = B2TestSupport.start(directory);
        for (String id : List.of("a", "b", "c")) { context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest(id, "原文" + id, null)); }
        var jdbc = new JdbcTemplate(context.getBean(DataSource.class));
        jdbc.update("UPDATE embedding_tasks SET status='SUCCEEDED'");
        jdbc.update("UPDATE documents SET vector_bytes=?,embedding_version=?,vector_completed_at=CURRENT_TIMESTAMP", new byte[1024], context.getBean(TextEmbedder.class).version());
        return context;
    }

    private static long count(ConfigurableApplicationContext context, String id) { return context.getBean(KnowledgeMapper.class).findDocument(id).hitCount(); }

    private static SearchService service(ConfigurableApplicationContext context, TextEmbedder embedder,
                                          SearchCandidateSource source, SearchHitCounter counter) {
        return new SearchService(context.getBean(RequestValidator.class), embedder, source, counter,
                context.getBean(TaskReadiness.class), context.getBean(VectorProperties.class));
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }
}
