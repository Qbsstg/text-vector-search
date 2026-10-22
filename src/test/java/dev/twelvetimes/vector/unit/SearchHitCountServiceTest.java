package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.search.SearchHitCountService;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchHitCountServiceTest {
    @TempDir Path directory;

    @Test
    void 仅最终ID按ASCII顺序自增且不覆盖失效或向量字段() {
        try (var context = data()) {
            List<String> calls = new ArrayList<>();
            var mapper = decorate(context.getBean(SearchMapper.class), (name, args, next) -> {
                if (name.equals("incrementHit")) { calls.add((String) args[0]); }
                return next.call();
            });
            context.getBean(DocumentQueryService.class).invalidate("a");
            counter(context, mapper, context.getBean(PlatformTransactionManager.class)).increment(List.of("b", "a"));
            assertEquals(List.of("a", "b"), calls);
            assertEquals(1, count(context, "a"));
            assertEquals(1, count(context, "b"));
            assertEquals(0, count(context, "c"));
            assertTrue(context.getBean(KnowledgeMapper.class).findDocument("a").invalidated());
        }
    }

    @Test
    void 第二项异常与行数零均整体回滚并且不自动重试() {
        try (var context = data()) {
            for (boolean returnZero : new boolean[] {false, true}) {
                AtomicInteger calls = new AtomicInteger();
                var mapper = decorate(context.getBean(SearchMapper.class), (name, args, next) -> {
                    if (name.equals("incrementHit") && calls.incrementAndGet() == 2) {
                        if (returnZero) { return 0; }
                        throw new DataAccessResourceFailureException("测试第二项失败");
                    }
                    return next.call();
                });
                assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                        () -> counter(context, mapper, context.getBean(PlatformTransactionManager.class)).increment(List.of("b", "a"))).code());
                assertEquals(0, count(context, "a"));
                assertEquals(0, count(context, "b"));
                assertEquals(2, calls.get());
                assertTrue(context.getBean(TaskReadiness.class).accepting());
            }
        }
    }

    @Test
    void BIGINT溢出回滚所有增量() {
        try (var context = data()) {
            new JdbcTemplate(context.getBean(DataSource.class)).update("UPDATE documents SET hit_count=? WHERE document_id='b'", Long.MAX_VALUE);
            assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                    () -> context.getBean(SearchHitCountService.class).increment(List.of("a", "b"))).code());
            assertEquals(0, count(context, "a"));
            assertEquals(Long.MAX_VALUE, count(context, "b"));
        }
    }

    @Test
    void 真commit后抛错不可用累计值确认或重放且进入降级() {
        try (var context = data()) {
            AtomicInteger updates = new AtomicInteger();
            var mapper = decorate(context.getBean(SearchMapper.class), (name, args, next) -> {
                assertEquals("incrementHit", name, "未知结果不能通过读取累计值猜测本请求状态");
                updates.incrementAndGet();
                return next.call();
            });
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) { super.commit(status); throw new TransactionSystemException("真实提交后测试异常"); }
            };
            assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                    () -> counter(context, mapper, manager).increment(List.of("b", "a"))).code());
            assertEquals(2, updates.get());
            assertEquals(1, count(context, "a"));
            assertEquals(1, count(context, "b"));
            assertEquals(TaskReadiness.State.DEGRADED, context.getBean(TaskReadiness.class).state());
        }
    }

    @Test
    void 提交未发生的未知反例同样不盲重试() {
        try (var context = data()) {
            AtomicInteger updates = new AtomicInteger();
            var mapper = decorate(context.getBean(SearchMapper.class), (name, args, next) -> {
                if (name.equals("incrementHit")) { updates.incrementAndGet(); }
                return next.call();
            });
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) { super.rollback(status); throw new TransactionSystemException("测试丢失提交结果"); }
            };
            assertEquals("RESULT_UNKNOWN", assertThrows(BusinessException.class,
                    () -> counter(context, mapper, manager).increment(List.of("a"))).code());
            assertEquals(1, updates.get());
            assertEquals(0, count(context, "a"));
            assertFalse(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 多个独立事务原子计数不丢失() throws Exception {
        try (var context = data()) {
            var counter = context.getBean(SearchHitCountService.class);
            var executor = Executors.newFixedThreadPool(4);
            CountDownLatch start = new CountDownLatch(1);
            try {
                var work = new ArrayList<Future<?>>();
                for (int index = 0; index < 12; index++) {
                    work.add(executor.submit(() -> { await(start); counter.increment(List.of("b", "a")); }));
                }
                start.countDown();
                for (var task : work) { task.get(8, TimeUnit.SECONDS); }
                assertEquals(12, count(context, "a"));
                assertEquals(12, count(context, "b"));
            } finally { executor.shutdownNow(); }
        }
    }

    @Test
    void 外层事务与停止阶段拒绝空命中不制造SQL() {
        try (var context = data()) {
            var mapper = decorate(context.getBean(SearchMapper.class), (name, args, next) -> { throw new AssertionError("此场景不得SQL"); });
            var counter = counter(context, mapper, context.getBean(PlatformTransactionManager.class));
            counter.increment(List.of());
            assertThrows(IllegalArgumentException.class, () -> counter.increment(List.of("a", "a")));
            new TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(status ->
                    assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> counter.increment(List.of("a"))).code()));
            context.getBean(TaskReadiness.class).stopping();
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class, () -> counter.increment(List.of("a"))).code());
        }
    }

    private ConfigurableApplicationContext data() {
        var context = B2TestSupport.start(directory);
        for (String id : List.of("a", "b", "c")) {
            context.getBean(DocumentAdmissionService.class).submit(new SubmitDocumentRequest(id, "", null));
        }
        return context;
    }

    private static long count(ConfigurableApplicationContext context, String id) {
        return context.getBean(KnowledgeMapper.class).findDocument(id).hitCount();
    }

    private static SearchHitCountService counter(ConfigurableApplicationContext context, SearchMapper mapper, PlatformTransactionManager manager) {
        return new SearchHitCountService(mapper, context.getBean(TaskReadiness.class), manager);
    }

    @FunctionalInterface private interface Next { Object call() throws Throwable; }
    @FunctionalInterface private interface Interceptor { Object call(String method, Object[] args, Next next) throws Throwable; }
    private static SearchMapper decorate(SearchMapper actual, Interceptor interceptor) {
        return (SearchMapper) Proxy.newProxyInstance(SearchMapper.class.getClassLoader(), new Class<?>[] {SearchMapper.class},
                (proxy, method, args) -> interceptor.call(method.getName(), args, () -> {
                    try { return method.invoke(actual, args); }
                    catch (InvocationTargetException exception) { throw exception.getCause(); }
                }));
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager actual;
        Manager(PlatformTransactionManager actual) { this.actual = actual; }
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) { return actual.getTransaction(definition); }
        @Override public void commit(TransactionStatus status) { actual.commit(status); }
        @Override public void rollback(TransactionStatus status) { actual.rollback(status); }
    }
}
