package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.search.SearchCandidate;
import dev.twelvetimes.vector.search.SearchCandidateSource;
import dev.twelvetimes.vector.search.SearchHitCounter;
import dev.twelvetimes.vector.search.SearchService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 结果参考不复用生产Top-K比较器；正常路径真实B1，人工向量仅用于数学和过滤边界。 */
class B4SearchIT {
    @TempDir Path directory;

    @Test
    void 成功零负同分无阈值且仅最终TopK计数() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            float[] query = env.bean(TextEmbedder.class).embed("ababa");
            float[] opposite = query.clone();
            for (int i = 0; i < opposite.length; i++) { opposite[i] = -opposite[i]; }
            env.fixture("z-positive", "真实向量方向", "default", query, "SUCCEEDED");
            env.fixture("a-zero", "", "default", new float[256], "SUCCEEDED");
            env.fixture("b-zero", "零向量", "other", new float[256], "SUCCEEDED");
            env.fixture("negative", "反向数学夹具", "default", opposite, "SUCCEEDED");
            var service = env.bean(SearchService.class);
            var top = service.search(new SearchRequest("ababa", 2, null));
            assertEquals(List.of("z-positive", "a-zero"), ids(top));
            assertEquals(1.0, top.hits().get(0).score(), 1e-12);
            assertEquals(0.0, top.hits().get(1).score());
            assertEquals(1, env.hits("z-positive"));
            assertEquals(1, env.hits("a-zero"));
            assertEquals(0, env.hits("b-zero"));
            assertEquals(0, env.hits("negative"));
            var all = service.search(new SearchRequest("ababa", 100, null));
            assertEquals(List.of("z-positive", "a-zero", "b-zero", "negative"), ids(all));
            assertEquals(-1.0, all.hits().get(3).score(), 1e-12);
            var emptyQuery = service.search(new SearchRequest("", 100, null));
            assertEquals(List.of("a-zero", "b-zero", "negative", "z-positive"), ids(emptyQuery));
            assertTrue(emptyQuery.hits().stream().allMatch(hit -> hit.score() == 0.0));
            assertEquals(3, env.hits("a-zero"));
            assertEquals(2, env.hits("negative"));
            var queryService = env.bean(DocumentQueryService.class);
            queryService.detail("a-zero");
            queryService.list(0, 20, null, null);
            assertEquals(3, env.hits("a-zero"));
        }
    }

    @Test
    void 状态双版本失效与渠道在TopK之前过滤() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            float[] q = env.bean(TextEmbedder.class).embed("ababa");
            for (String state : List.of("QUEUED", "RUNNING", "FAILED")) {
                env.fixture("state-" + state, "非成功", "A", q, state);
            }
            env.fixture("no-vector", "未就绪", "A", null, "SUCCEEDED");
            for (String id : List.of("invalid", "old-task", "old-doc", "other", "case", "valid")) {
                env.fixture(id, "过滤夹具", id.equals("other") ? "B" : id.equals("case") ? "a" : "A", q, "SUCCEEDED");
            }
            env.sql("UPDATE documents SET invalidated=TRUE,invalidated_at=CURRENT_TIMESTAMP WHERE document_id='invalid'");
            env.sql("UPDATE documents SET embedding_version='old' WHERE document_id='old-doc'");
            env.sql("UPDATE embedding_tasks SET embedding_version='old' WHERE document_id='old-task'");
            var service = env.bean(SearchService.class);
            assertEquals(List.of("valid"), ids(service.search(new SearchRequest("ababa", 1, " A "))));
            assertEquals(List.of("case", "other", "valid"), ids(service.search(new SearchRequest("ababa", 100, null))));
            assertEquals(0, env.hits("invalid"));
            assertEquals(0, env.hits("old-task"));
            assertEquals(0, env.hits("old-doc"));
            assertEquals(2, env.hits("valid"));
        }
    }

    @Test
    void 固定随机候选与独立全排序参考一致且无隐式截断() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            Random random = new Random(20261023L);
            float[] q = env.bean(TextEmbedder.class).embed("ababa");
            record Expected(String id, double score) { }
            List<Expected> expected = new ArrayList<>();
            // 113条超过单页100，证明候选不被分页截断；整数轴夹具方便独立数学参考。
            for (int index = 0; index < 113; index++) {
                float[] vector = new float[256];
                vector[random.nextInt(256)] = random.nextInt(5) - 2;
                vector[random.nextInt(256)] += random.nextInt(5) - 2;
                String id = String.format(java.util.Locale.ROOT, "d-%03d", index);
                env.fixture(id, id, "default", vector, "SUCCEEDED");
                double dot = 0, left = 0, right = 0;
                for (int i = 0; i < 256; i++) {
                    dot += (double) q[i] * vector[i];
                    left += (double) q[i] * q[i];
                    right += (double) vector[i] * vector[i];
                }
                double score = right == 0 ? 0 : dot / (Math.sqrt(left) * Math.sqrt(right));
                expected.add(new Expected(id, score == 0 ? 0 : score));
            }
            expected.sort(Comparator.comparingDouble(Expected::score).reversed().thenComparing(Expected::id));
            var actual = env.bean(SearchService.class).search(new SearchRequest("ababa", 100, null));
            assertEquals(expected.subList(0, 100).stream().map(Expected::id).toList(), ids(actual));
            for (int i = 0; i < 100; i++) { assertEquals(expected.get(i).score(), actual.hits().get(i).score(), 1e-12); }
            for (Expected item : expected) {
                assertEquals(expected.indexOf(item) < 100 ? 1 : 0, env.hits(item.id()));
            }
        }
    }

    @Test
    void 快照之后失效仍返回并计数且CPU不占单连接池() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null, "--spring.datasource.hikari.maximum-pool-size=1")) {
            env.fixture("snapshot", "原文", "default", env.bean(TextEmbedder.class).embed("ababa"), "SUCCEEDED");
            CountDownLatch loaded = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            SearchCandidateSource original = env.bean(SearchCandidateSource.class);
            var service = env.service(env.bean(TextEmbedder.class), (version, channel) -> {
                List<SearchCandidate> result = original.load(version, channel);
                assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                assertFalse(TransactionSynchronizationManager.hasResource(env.bean(DataSource.class)));
                loaded.countDown();
                try { await(release); }
                catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                return result;
            }, env.bean(SearchHitCounter.class), 1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var result = executor.submit(() -> service.search(new SearchRequest("ababa", 1, null)));
                await(loaded);
                var invalidated = env.bean(DocumentQueryService.class).invalidate("snapshot");
                env.fixture("during-cpu", "短写入不被单连接池阻塞", "default", null, "FAILED");
                release.countDown();
                assertEquals(List.of("snapshot"), ids(result.get(8, TimeUnit.SECONDS)));
                var after = env.bean(KnowledgeMapper.class).findDocument("snapshot");
                assertTrue(after.invalidated());
                assertEquals(invalidated.invalidatedAt(), after.invalidatedAt());
                assertEquals(1, after.hitCount());
                assertTrue(env.bean(SearchService.class).search(new SearchRequest("ababa", 1, null)).hits().isEmpty());
            } finally {
                release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void 坏候选和超容量失败不能计数且许可释放() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            env.fixture("bad", "坏候选", "default", new float[256], "SUCCEEDED");
            var bad = env.service(env.bean(TextEmbedder.class), (v, c) -> List.of(new SearchCandidate("bad", "default", new byte[3], "")),
                    env.bean(SearchHitCounter.class), 1);
            for (int retry = 0; retry < 2; retry++) {
                assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                        () -> bad.search(new SearchRequest("ababa", 1, null))).code());
            }
            assertEquals(0, env.hits("bad"));
            var tinyProperties = env.properties(1, 1);
            env.fixture("extra", "超出测试容量", "default", new float[256], "SUCCEEDED");
            var reader = new dev.twelvetimes.vector.search.SearchCandidateReader(env.bean(dev.twelvetimes.vector.persistence.SearchMapper.class),
                    env.bean(dev.twelvetimes.vector.task.TaskReadiness.class), tinyProperties,
                    env.bean(org.springframework.transaction.PlatformTransactionManager.class));
            assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                    () -> reader.load(env.bean(TextEmbedder.class).version(), null)).code());
            assertEquals(0, env.hits("extra"));
        }
    }

    @Test
    void 候选只读有界前缀且返回字节不共享可变存储() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            String raw = "😀".repeat(1000);
            env.fixture("bounded", raw, "default", new float[256], "SUCCEEDED");
            var candidates = env.bean(SearchCandidateSource.class).load(env.bean(TextEmbedder.class).version(), null);
            assertEquals(1, candidates.size());
            var candidate = candidates.get(0);
            assertTrue(candidate.prefix().codePointCount(0, candidate.prefix().length()) <= 400);
            assertTrue(candidate.prefix().length() < raw.length(), "候选不能携带全量原文CLOB");
            byte[] changed = candidate.vectorBytes();
            changed[0] = 127;
            assertEquals(0, candidate.vectorBytes()[0], "修改读出的数组不应污染候选快照");
            var hits = env.bean(SearchService.class).search(new SearchRequest("", 1, null)).hits();
            assertEquals("😀".repeat(200), hits.get(0).snippet());
            B4Evidence.record("BOUNDED", "rawUtf16=" + raw.length() + ";prefixUtf16=" + candidate.prefix().length()
                    + ";snippetCodePoints=200;defensiveCopy=true");
        }
    }

    static List<String> ids(dev.twelvetimes.vector.api.contract.ApiModels.SearchResponse response) {
        return response.hits().stream().map(dev.twelvetimes.vector.api.contract.ApiModels.SearchHit::documentId).toList();
    }
}
