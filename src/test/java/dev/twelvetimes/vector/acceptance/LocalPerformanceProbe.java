package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.CosineSimilarity;
import dev.twelvetimes.vector.search.ScoredDocument;
import dev.twelvetimes.vector.search.SearchCandidateReader;
import dev.twelvetimes.vector.search.SearchHitCountService;
import dev.twelvetimes.vector.search.TopKSelector;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 显式手动运行的同组件分阶段微基准，不匹配默认Test/IT发现规则。
 * 每次新建临时文件H2，评分先物化以分离计时，故不等价于生产HTTP trace或生产峰值内存。
 * 原始纳秒与请求分类全部保存，不设吞吐/时延阈值，不替代独立客户端对Linux JAR的实测。
 */
public final class LocalPerformanceProbe {
    private LocalPerformanceProbe() { }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) { throw new IllegalArgumentException("参数为新的绝对证据JSON路径"); }
        Path output = Path.of(args[0]);
        if (!output.isAbsolute() || Files.exists(output)) { throw new IllegalArgumentException("证据路径必须绝对且不存在"); }
        Path root = Files.createTempDirectory("12times-local-components-").toAbsolutePath();
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("mode", "COMPONENT_MICROBENCHMARK_NOT_HTTP_TRACE");
        report.put("startedAt", Instant.now().toString());
        report.put("java", System.getProperty("java.runtime.version"));
        report.put("os", System.getProperty("os.name") + " " + System.getProperty("os.arch"));
        report.put("processors", Runtime.getRuntime().availableProcessors());
        report.put("isolatedDataRoot", root.toString());
        report.put("scope", "真实B1/真实H2/当前组件；预构造合成成功文档；评分含解码并物化；无HTTP、CPU/RSS/GC或SLA结论");
        List<Map<String, Object>> stages = new ArrayList<>();
        report.put("stages", stages);
        for (int n : List.of(100, 1000, 5000)) {
            try (var env = new B4QaEnvironment(root.resolve("n-" + n), false, false, null)) {
                TextEmbedder embedder = env.bean(TextEmbedder.class);
                report.put("embeddingVersion", embedder.version());
                seed(env, n);
                for (int k : List.of(1, 10, 100)) {
                    measure(env, n, k, true);
                    for (int sample = 0; sample < 3; sample++) { stages.add(measure(env, n, k, false)); }
                }
            }
        }
        try (var env = new B4QaEnvironment(root.resolve("contention"), false, false, null,
                "--spring.datasource.url=jdbc:h2:file:" + root.resolve("contention/knowledge") + ";WRITE_DELAY=0;LOCK_TIMEOUT=200",
                "--spring.datasource.hikari.maximum-pool-size=8")) {
            seed(env, 8);
            var counter = new InstrumentedCounter(env);
            report.put("hotspot", parallel(env, counter, true));
            report.put("distributed", parallel(env, counter, false));
            report.put("controlledSlowDatabase", slowDatabase(env, counter));
            assertEquals(0, env.bean(TaskDispatcher.class).inFlightCount());
            report.put("finalTaskInFlight", env.bean(TaskDispatcher.class).inFlightCount());
            report.put("finalReadiness", env.bean(TaskReadiness.class).state().name());
        }
        report.put("finishedAt", Instant.now().toString());
        Files.createDirectories(output.getParent());
        Files.writeString(output, new ObjectMapper().writerWithDefaultPrettyPrinter().writeValueAsString(report) + "\n");
        System.out.println("COMPLETE " + output + "；临时数据目录保留，不删除：" + root);
    }

    /** 用8种真实B1向量批量准备成功行；准备耗时不混入请求测量，数据形状明确为合成夹具。 */
    private static void seed(B4QaEnvironment env, int count) throws Exception {
        TextEmbedder embedder = env.bean(TextEmbedder.class);
        List<byte[]> vectors = new ArrayList<>();
        for (int i = 0; i < 8; i++) { vectors.add(VectorCodec.encode(embedder.embed("性能合成文本-" + i))); }
        try (var connection = env.bean(DataSource.class).getConnection()) {
            connection.setAutoCommit(false);
            try (var doc = connection.prepareStatement("INSERT INTO documents(document_id,raw_text,channel,submitted_at,vector_bytes,embedding_version,vector_completed_at) VALUES(?,?,?,CURRENT_TIMESTAMP,?,?,CURRENT_TIMESTAMP)");
                 var task = connection.prepareStatement("INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at,finished_at) VALUES(?,?,'SUCCEEDED',?,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)")) {
                for (int i = 0; i < count; i++) {
                    String id = id(i);
                    doc.setString(1, id);
                    doc.setString(2, "性能合成文本-" + (i % 8));
                    doc.setString(3, "micro");
                    doc.setBytes(4, vectors.get(i % 8));
                    doc.setString(5, embedder.version());
                    doc.addBatch();
                    task.setString(1, UUID.randomUUID().toString());
                    task.setString(2, id);
                    task.setString(3, embedder.version());
                    task.addBatch();
                }
                doc.executeBatch();
                task.executeBatch();
                connection.commit();
            } catch (Exception exception) {
                connection.rollback();
                throw exception;
            }
        }
        assertEquals(count, env.bean(SearchMapper.class).countDocuments());
    }

    /** 阶段使用同一套真实组件，但分离计时改变了对象寿命，不据此推导生产HTTP内存峰值。 */
    private static Map<String, Object> measure(B4QaEnvironment env, int n, int k, boolean warmup) {
        TextEmbedder embedder = env.bean(TextEmbedder.class);
        long start = System.nanoTime();
        float[] query = embedder.embed("性能合成文本-0");
        long embedded = System.nanoTime();
        var candidates = env.bean(SearchCandidateReader.class).load(embedder.version(), "micro");
        long loaded = System.nanoTime();
        assertEquals(n, candidates.size());
        List<ScoredDocument> scores = new ArrayList<>();
        for (var candidate : candidates) {
            scores.add(new ScoredDocument(candidate.documentId(), CosineSimilarity.score(query, VectorCodec.decode(candidate.vectorBytes()))));
        }
        long scored = System.nanoTime();
        var top = TopKSelector.select(scores, k);
        long selected = System.nanoTime();
        env.bean(SearchHitCountService.class).increment(top.stream().map(ScoredDocument::documentId).toList());
        long counted = System.nanoTime();
        assertEquals(Math.min(k, n), top.size());
        Map<String, Object> sample = new LinkedHashMap<>();
        sample.put("n", n);
        sample.put("k", k);
        sample.put("warmup", warmup);
        sample.put("queryEmbeddingNs", embedded - start);
        sample.put("candidateLoadNs", loaded - embedded);
        sample.put("decodeScoreMaterializeNs", scored - loaded);
        sample.put("topKNs", selected - scored);
        sample.put("countIncludingCommitNs", counted - selected);
        sample.put("componentWallNs", counted - start);
        return sample;
    }

    /** 并发事务只按明确返回成功数对账；若出现未知提交则单列，不能从累计值推断那一笔。 */
    private static Map<String, Object> parallel(B4QaEnvironment env, InstrumentedCounter counter, boolean hot) throws Exception {
        long before = sum(env, 4);
        CyclicBarrier barrier = new CyclicBarrier(4);
        var pool = Executors.newFixedThreadPool(4);
        List<java.util.concurrent.Future<List<Map<String, Object>>>> futures = new ArrayList<>();
        try {
            for (int worker = 0; worker < 4; worker++) {
                int index = worker;
                futures.add(pool.submit(() -> {
                    barrier.await(10, TimeUnit.SECONDS);
                    List<Map<String, Object>> result = new ArrayList<>();
                    for (int call = 0; call < 20; call++) { result.add(counter.call(List.of(id(hot ? 0 : index)))); }
                    return result;
                }));
            }
            List<Map<String, Object>> samples = new ArrayList<>();
            for (var future : futures) { samples.addAll(future.get(30, TimeUnit.SECONDS)); }
            long committed = samples.stream().filter(x -> x.get("outcome").equals("CONFIRMED_COMMITTED")).count();
            long unknown = samples.stream().filter(x -> x.get("outcome").equals("UNKNOWN")).count();
            long after = sum(env, 4);
            if (unknown == 0) { assertEquals(committed, after - before); }
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("mode", hot ? "four_workers_one_row" : "four_workers_four_rows");
            result.put("attempted", samples.size());
            result.put("confirmedCommitted", committed);
            result.put("unknown", unknown);
            result.put("persistentDelta", after - before);
            result.put("exactReconciliation", unknown == 0);
            result.put("samples", samples);
            return result;
        } finally {
            pool.shutdown();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    /** 单独JDBC事务持有目标行，等待真实H2锁超时后释放；随后同一搜索实例必须能再次成功。 */
    private static Map<String, Object> slowDatabase(B4QaEnvironment env, InstrumentedCounter counter) throws Exception {
        var service = env.service(env.bean(TextEmbedder.class), env.bean(SearchCandidateReader.class), counter.service, 1);
        long before = env.hits(id(0));
        String outcome;
        long elapsed;
        try (var lock = env.bean(DataSource.class).getConnection()) {
            lock.setAutoCommit(false);
            try (var update = lock.prepareStatement("UPDATE documents SET hit_count=hit_count WHERE document_id=?")) {
                update.setString(1, id(0));
                assertEquals(1, update.executeUpdate());
            }
            long start = System.nanoTime();
            try {
                service.search(new SearchRequest("性能合成文本-0", 1, "micro"));
                throw new AssertionError("行锁必须导致本次隔离锁超时失败");
            } catch (BusinessException exception) {
                outcome = exception.code();
                assertEquals("DATABASE_UNAVAILABLE", outcome);
            } finally {
                elapsed = System.nanoTime() - start;
                lock.rollback();
            }
        }
        assertEquals(before, env.hits(id(0)));
        assertTrue(env.bean(TaskReadiness.class).accepting());
        var recovered = service.search(new SearchRequest("性能合成文本-0", 1, "micro"));
        assertEquals(id(0), recovered.hits().get(0).documentId());
        assertEquals(before + 1, env.hits(id(0)));
        assertFalse(env.bean(TaskDispatcher.class).inFlightCount() > 0);
        return Map.of("fault", "isolated JDBC row lock, H2 LOCK_TIMEOUT=200ms", "error", outcome,
                "elapsedNs", elapsed, "failedPersistentDelta", 0, "recoveryPersistentDelta", 1,
                "sameSearchPermitReusable", true, "readiness", env.bean(TaskReadiness.class).state().name());
    }

    /** SQL阶段含锁等待和执行，不能把它叫纯锁等待；真实commit单独计时。 */
    private static final class InstrumentedCounter {
        private final ThreadLocal<Map<String, Object>> current = new ThreadLocal<>();
        private final SearchHitCountService service;

        InstrumentedCounter(B4QaEnvironment env) {
            SearchMapper decorated = env.mapper((name, args, proceed) -> {
                long start = System.nanoTime();
                try { return proceed.call(); }
                finally {
                    if (current.get() != null && name.equals("incrementHit")) {
                        current.get().put("sqlUpdateIncludingLockWaitNs", System.nanoTime() - start);
                    }
                }
            });
            PlatformTransactionManager original = env.bean(PlatformTransactionManager.class);
            PlatformTransactionManager timed = new PlatformTransactionManager() {
                @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
                    return original.getTransaction(definition);
                }
                @Override public void commit(TransactionStatus status) {
                    long start = System.nanoTime();
                    try { original.commit(status); }
                    finally { if (current.get() != null) { current.get().put("physicalCommitNs", System.nanoTime() - start); } }
                }
                @Override public void rollback(TransactionStatus status) { original.rollback(status); }
            };
            service = new SearchHitCountService(decorated, env.bean(TaskReadiness.class), timed);
        }

        Map<String, Object> call(List<String> ids) {
            Map<String, Object> sample = new LinkedHashMap<>();
            current.set(sample);
            long start = System.nanoTime();
            try {
                service.increment(ids);
                sample.put("outcome", "CONFIRMED_COMMITTED");
            } catch (BusinessException exception) {
                sample.put("outcome", exception.code().equals("RESULT_UNKNOWN") ? "UNKNOWN" : "CONFIRMED_FAILED");
                sample.put("code", exception.code());
            } finally {
                sample.put("wallNs", System.nanoTime() - start);
                current.remove();
            }
            return sample;
        }
    }

    private static long sum(B4QaEnvironment env, int count) {
        long result = 0;
        for (int i = 0; i < count; i++) { result += env.hits(id(i)); }
        return result;
    }

    private static String id(int index) { return String.format(java.util.Locale.ROOT, "P-%05d", index); }
}
