package dev.twelvetimes.vector.acceptance;

import com.zaxxer.hikari.HikariDataSource;
import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.SearchCandidateReader;
import dev.twelvetimes.vector.search.SearchCandidateSource;
import dev.twelvetimes.vector.search.SearchHitCountService;
import dev.twelvetimes.vector.search.SearchHitCounter;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 将实际SQL并发和默认许可过载分层，计数断言只按已确认提交次数对账。 */
class B4CountAndResourceIT {
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"第二项前失败", "第二项后失败", "返回零行", "BIGINT溢出"})
    void 最终命中多行事务任何失败整体回滚(String mode) throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            for (String id : List.of("a", "b")) { env.fixture(id, id, "default", new float[256], "SUCCEEDED"); }
            if (mode.equals("BIGINT溢出")) { env.sql("UPDATE documents SET hit_count=? WHERE document_id='b'", Long.MAX_VALUE); }
            List<String> order = new CopyOnWriteArrayList<>();
            var mapper = env.mapper((name, args, proceed) -> {
                if (name.equals("incrementHit")) {
                    String id = (String) args[0];
                    order.add(id);
                    if (id.equals("b")) {
                        if (mode.equals("返回零行")) { return 0; }
                        if (mode.equals("第二项前失败")) { throw new DataAccessResourceFailureException("仅测试：SQL错误不得泄露"); }
                        if (mode.equals("第二项后失败")) {
                            proceed.call();
                            throw new DataAccessResourceFailureException("仅测试：写后失败");
                        }
                    }
                }
                return proceed.call();
            });
            var counter = counter(env, mapper, env.bean(PlatformTransactionManager.class));
            var failure = assertThrows(BusinessException.class, () -> counter.increment(List.of("b", "a")));
            assertEquals(503, failure.status());
            assertEquals("DATABASE_UNAVAILABLE", failure.code());
            assertEquals(List.of("a", "b"), order, "实际SQL固定ID顺序，且不盲重试");
            assertEquals(0, env.hits("a"));
            assertEquals(mode.equals("BIGINT溢出") ? Long.MAX_VALUE : 0, env.hits("b"));
            assertTrue(env.bean(TaskReadiness.class).accepting());
            B4Evidence.record("ROLLBACK-" + mode, "实际SQL顺序=" + order + ";a=" + env.hits("a") + ";b=" + env.hits("b"));
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 提交未知不读取累计数不重放且实际增量与真实提交一致(boolean physicallyCommitted) throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            env.fixture("a", "原文", "default", new float[256], "SUCCEEDED");
            AtomicInteger sqlCalls = new AtomicInteger();
            var mapper = env.mapper((name, args, proceed) -> {
                assertEquals("incrementHit", name, "计数未知不准读取候选或累计值推断本次提交");
                sqlCalls.incrementAndGet();
                return proceed.call();
            });
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    if (physicallyCommitted) { super.commit(status); }
                    else { super.rollback(status); }
                    throw new TransactionSystemException("仅测试：调用方无法确认commit结果");
                }
            };
            var counter = counter(env, mapper, manager);
            var unknown = assertThrows(BusinessException.class, () -> counter.increment(List.of("a")));
            assertEquals("RESULT_UNKNOWN", unknown.code());
            assertEquals(1, sqlCalls.get());
            assertEquals(physicallyCommitted ? 1 : 0, env.hits("a"));
            assertEquals(TaskReadiness.State.DEGRADED, env.bean(TaskReadiness.class).state());
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> counter.increment(List.of("a"))).code());
            assertEquals(1, sqlCalls.get());
            B4Evidence.record("UNKNOWN-" + physicallyCommitted, "物理提交=" + physicallyCommitted + ";sqlCalls=" + sqlCalls.get()
                    + ";hitCount=" + env.hits("a") + ";state=" + env.bean(TaskReadiness.class).state());
        }
    }

    @Test
    void 八路实际搜索多轮并发只对最终热点原子累加() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            for (String id : List.of("a", "b", "not-selected")) { env.fixture(id, id, "default", new float[256], "SUCCEEDED"); }
            var barrier = new CyclicBarrier(8);
            AtomicInteger committed = new AtomicInteger();
            SearchHitCounter original = env.bean(SearchHitCounter.class);
            var service = env.service(env.bean(TextEmbedder.class), env.bean(SearchCandidateSource.class), ids -> {
                try { barrier.await(8, TimeUnit.SECONDS); }
                catch (Exception exception) { throw new AssertionError(exception); }
                original.increment(ids);
                committed.incrementAndGet();
            }, 8);
            var executor = Executors.newFixedThreadPool(8);
            try {
                List<java.util.concurrent.Future<?>> work = new ArrayList<>();
                for (int thread = 0; thread < 8; thread++) {
                    work.add(executor.submit(() -> {
                        for (int round = 0; round < 8; round++) {
                            assertEquals(List.of("a", "b"), B4SearchIT.ids(service.search(new SearchRequest("", 2, null))));
                        }
                    }));
                }
                for (var future : work) { future.get(25, TimeUnit.SECONDS); }
                assertEquals(64, committed.get());
                assertEquals(64, env.hits("a"));
                assertEquals(64, env.hits("b"));
                assertEquals(0, env.hits("not-selected"));
                assertEquals(1, env.bean(dev.twelvetimes.vector.config.VectorProperties.class).searchPermits(), "生产值未更改");
                B4Evidence.record("ATOMIC", "测试许可=8;生产许可=1;confirmedCommitted=" + committed.get()
                        + ";a=" + env.hits("a") + ";b=" + env.hits("b") + ";notSelected=" + env.hits("not-selected"));
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(12, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void 默认搜索许可持有到真实commit完成且所有出口释放() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            env.fixture("a", "", "default", new float[256], "SUCCEEDED");
            CountDownLatch atCommit = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicBoolean first = new AtomicBoolean(true);
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    if (first.compareAndSet(true, false)) {
                        atCommit.countDown();
                        try { await(release); }
                        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                    }
                    super.commit(status);
                }
            };
            var service = env.service(env.bean(TextEmbedder.class), env.bean(SearchCandidateSource.class),
                    counter(env, env.bean(SearchMapper.class), manager), 1);
            var executor = Executors.newSingleThreadExecutor();
            try {
                var request = executor.submit(() -> service.search(new SearchRequest("", 1, null)));
                await(atCommit);
                assertEquals(0, env.hits("a"), "另连接在物理commit前看不到增量");
                var busy = assertThrows(BusinessException.class, () -> service.search(new SearchRequest("", 1, null)));
                assertEquals(429, busy.status());
                assertEquals("SEARCH_BUSY", busy.code());
                release.countDown();
                request.get(8, TimeUnit.SECONDS);
                assertEquals(1, env.hits("a"));
                service.search(new SearchRequest("", 1, null));
                assertEquals(2, env.hits("a"));
            } finally {
                release.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void CPU暂停时无事务连接关闭后迟到搜索零SQL且不计数() throws Exception {
        var env = new B4QaEnvironment(directory, false, false, null, "--spring.datasource.hikari.maximum-pool-size=1");
        CountDownLatch cpu = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicBoolean closed = new AtomicBoolean();
        AtomicInteger lateCalls = new AtomicInteger();
        var executor = Executors.newSingleThreadExecutor();
        try {
            env.fixture("a", "", "default", new float[256], "SUCCEEDED");
            TextEmbedder original = env.bean(TextEmbedder.class);
            TextEmbedder paused = new TextEmbedder() {
                @Override
                public float[] embed(String text) {
                    float[] vector = original.embed(text);
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionSynchronizationManager.hasResource(env.bean(DataSource.class)));
                    cpu.countDown();
                    try { await(release); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                    return vector;
                }
                @Override public String version() { return original.version(); }
            };
            var mapper = env.mapper((name, args, proceed) -> {
                if (closed.get()) { lateCalls.incrementAndGet(); throw new AssertionError("关闭后不准调用SQL"); }
                return proceed.call();
            });
            var reader = new SearchCandidateReader(mapper, env.bean(TaskReadiness.class),
                    env.bean(dev.twelvetimes.vector.config.VectorProperties.class), env.bean(PlatformTransactionManager.class));
            var service = env.service(paused, reader, counter(env, mapper, env.bean(PlatformTransactionManager.class)), 1);
            var future = executor.submit(() -> assertThrows(BusinessException.class,
                    () -> service.search(new SearchRequest("ababa", 1, null))));
            await(cpu);
            // 单连接池仍可写入，且生产关闭可完成，证明CPU不持连接或B3短写屏障。
            env.fixture("during", "CPU阶段短写", "default", null, "FAILED");
            HikariDataSource dataSource = env.bean(HikariDataSource.class);
            TaskReadiness readiness = env.bean(TaskReadiness.class);
            env.close();
            assertTrue(dataSource.isClosed());
            assertFalse(readiness.writeBarrier().isOpen());
            closed.set(true);
            release.countDown();
            assertEquals("SERVICE_NOT_READY", future.get(8, TimeUnit.SECONDS).code());
            assertEquals(0, lateCalls.get());
            try (var connection = java.sql.DriverManager.getConnection(dataSource.getJdbcUrl(), "sa", "");
                 var statement = connection.createStatement(); var row = statement.executeQuery("SELECT hit_count FROM documents WHERE document_id='a'")) {
                assertTrue(row.next());
                assertEquals(0, row.getLong(1));
                B4Evidence.record("STOPPED", "DSclosed=" + dataSource.isClosed() + ";barrierOpen=" + readiness.writeBarrier().isOpen()
                        + ";lateSQL=" + lateCalls.get() + ";hitCount=" + row.getLong(1));
            }
        } finally {
            release.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            env.close();
        }
    }

    @Test
    void 搜索读取计数入口拒绝外层真实事务() throws Exception {
        try (var env = new B4QaEnvironment(directory, false, false, null)) {
            env.fixture("a", "", "default", new float[256], "SUCCEEDED");
            var tx = new TransactionTemplate(env.bean(PlatformTransactionManager.class));
            tx.executeWithoutResult(status -> {
                assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> env.bean(dev.twelvetimes.vector.search.SearchService.class)
                        .search(new SearchRequest("", 1, null))).code());
                assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                        () -> env.bean(SearchCandidateSource.class).load(env.bean(TextEmbedder.class).version(), null)).code());
                assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                        () -> env.bean(SearchHitCounter.class).increment(List.of("a"))).code());
            });
            assertEquals(0, env.hits("a"));
        }
    }

    @Test
    void 真实关闭等待B4计数物理提交后才销毁数据源() throws Exception {
        var env = new B4QaEnvironment(directory, false, false, null);
        CountDownLatch beforeCommit = new CountDownLatch(1);
        CountDownLatch allowCommit = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            env.fixture("a", "计数排空夹具", "default", new float[256], "SUCCEEDED");
            HikariDataSource dataSource = env.bean(HikariDataSource.class);
            String url = dataSource.getJdbcUrl();
            TaskReadiness readiness = env.bean(TaskReadiness.class);
            List<String> events = new CopyOnWriteArrayList<>();
            var manager = new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
                @Override
                public void commit(TransactionStatus status) {
                    events.add("COUNT_BEFORE_COMMIT");
                    beforeCommit.countDown();
                    try { await(allowCommit); }
                    catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
                    super.commit(status);
                    events.add("COUNT_COMMITTED");
                }
            };
            var service = env.service(env.bean(TextEmbedder.class), env.bean(SearchCandidateSource.class),
                    counter(env, env.bean(SearchMapper.class), manager), 1);
            var searching = executor.submit(() -> service.search(new SearchRequest("", 1, null)));
            await(beforeCommit);
            var closing = executor.submit(() -> {
                env.close();
                events.add("CONTEXT_CLOSE_RETURNED");
            });
            // 关闭新许可后，关闭方正在等待已进入的计数事务，不得提前销毁H2连接池。
            until(() -> !readiness.writeBarrier().isOpen());
            assertEquals(TaskReadiness.State.STOPPING, readiness.state());
            assertFalse(closing.isDone());
            assertFalse(dataSource.isClosed());
            assertEquals(List.of("COUNT_BEFORE_COMMIT"), List.copyOf(events));
            // 上下文已开始close，不再从其取Bean；用独立连接验证未提交增量不可见。
            assertEquals(0, independentlyReadHitCount(url));
            allowCommit.countDown();
            assertEquals(List.of("a"), B4SearchIT.ids(searching.get(8, TimeUnit.SECONDS)));
            closing.get(8, TimeUnit.SECONDS);
            assertEquals(List.of("COUNT_BEFORE_COMMIT", "COUNT_COMMITTED", "CONTEXT_CLOSE_RETURNED"), List.copyOf(events));
            assertTrue(dataSource.isClosed());
            assertEquals(TaskReadiness.State.STOPPED, readiness.state());
            assertEquals(1, independentlyReadHitCount(url));
            B4Evidence.record("COUNT_DRAIN", "events=" + events + ";beforeCommitVisible=0;afterClosePersistent=1;DSclosed="
                    + dataSource.isClosed() + ";state=" + readiness.state());
        } finally {
            allowCommit.countDown();
            executor.shutdown();
            assertTrue(executor.awaitTermination(12, TimeUnit.SECONDS));
            env.close();
        }
    }

    private static long independentlyReadHitCount(String url) throws Exception {
        try (var connection = java.sql.DriverManager.getConnection(url, "sa", "");
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT hit_count FROM documents WHERE document_id='a'")) {
            assertTrue(row.next());
            return row.getLong(1);
        }
    }

    private static SearchHitCountService counter(B4QaEnvironment env, SearchMapper mapper, PlatformTransactionManager manager) {
        return new SearchHitCountService(mapper, env.bean(TaskReadiness.class), manager);
    }
}
