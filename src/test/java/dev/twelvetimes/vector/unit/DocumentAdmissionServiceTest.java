package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DocumentAdmissionServiceTest {
    @TempDir Path directory;

    @Test
    void 接收真实提交两表且原文版本时间与计数保留() {
        try (var context = B2TestSupport.start(directory)) {
            var service = context.getBean(DocumentAdmissionService.class);
            var submission = service.submit(new SubmitDocumentRequest("a", "", null));
            var mapper = context.getBean(KnowledgeMapper.class);
            var row = mapper.findTask(submission.taskId());
            assertEquals("a", row.documentId());
            assertEquals("", row.text());
            assertEquals("default", row.channel());
            assertEquals("QUEUED", row.status());
            assertEquals(0, row.hitCount());
            assertEquals(context.getBean(TextEmbedder.class).version(), row.embeddingVersion());
            assertEquals(1, mapper.countDocuments());
            assertEquals(1, mapper.countPending());
        }
    }

    @Test
    void 第二表插入失败真实回滚第一表() {
        try (var context = B2TestSupport.start(directory)) {
            KnowledgeMapper actual = context.getBean(KnowledgeMapper.class);
            KnowledgeMapper mapper = (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                    new Class<?>[] {KnowledgeMapper.class}, (proxy, method, arguments) -> {
                        if (method.getName().equals("insertTask")) {
                            throw new DataAccessResourceFailureException("测试故障");
                        }
                        try { return method.invoke(actual, arguments); }
                        catch (InvocationTargetException exception) { throw exception.getCause(); }
                    });
            var service = service(context, mapper, context.getBean(PlatformTransactionManager.class));
            assertEquals("DATABASE_UNAVAILABLE", assertThrows(BusinessException.class,
                    () -> service.submit(new SubmitDocumentRequest("rollback", "原文", null))).code());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countPending());
        }
    }

    @Test
    void 接收锁一直持有到真实commit完成最后名额不超收() throws Exception {
        try (var context = B2TestSupport.start(directory, "--vector.max-pending-tasks=1")) {
            CountDownLatch beforeCommit = new CountDownLatch(1);
            CountDownLatch allowCommit = new CountDownLatch(1);
            PlatformTransactionManager manager = context.getBean(PlatformTransactionManager.class);
            AtomicBoolean first = new AtomicBoolean(true);
            var decorated = new Manager(manager) {
                @Override public void commit(TransactionStatus status) {
                    if (first.compareAndSet(true, false)) {
                        beforeCommit.countDown();
                        await(allowCommit);
                    }
                    super.commit(status);
                }
            };
            var mapper = context.getBean(KnowledgeMapper.class);
            var service = service(context, mapper, decorated);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var one = executor.submit(() -> service.submit(new SubmitDocumentRequest("first", "", null)));
                assertTrue(beforeCommit.await(5, TimeUnit.SECONDS));
                assertEquals(0, mapper.countDocuments(), "另一个连接在提交前不能看到第一表");
                CountDownLatch secondStarted = new CountDownLatch(1);
                var two = executor.submit(() -> {
                    secondStarted.countDown();
                    return assertThrows(BusinessException.class,
                            () -> service.submit(new SubmitDocumentRequest("second", "", null))).code();
                });
                assertTrue(secondStarted.await(5, TimeUnit.SECONDS));
                assertFalse(two.isDone());
                allowCommit.countDown();
                assertEquals("first", one.get(5, TimeUnit.SECONDS).documentId());
                assertEquals("CAPACITY_EXCEEDED", two.get(5, TimeUnit.SECONDS));
                assertEquals(1, mapper.countDocuments());
                assertEquals(1, mapper.countPending());
            } finally {
                allowCommit.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void 重复ID并发只接受一次且不覆盖() throws Exception {
        try (var context = B2TestSupport.start(directory)) {
            var service = context.getBean(DocumentAdmissionService.class);
            var executor = Executors.newFixedThreadPool(2);
            CountDownLatch start = new CountDownLatch(1);
            try {
                var work = new Callable<String>() {
                    @Override public String call() {
                        await(start);
                        try {
                            service.submit(new SubmitDocumentRequest("same", "原文", null));
                            return "ACCEPTED";
                        } catch (BusinessException exception) { return exception.code(); }
                    }
                };
                var first = executor.submit(work);
                var second = executor.submit(work);
                start.countDown();
                assertEquals(Set.of("ACCEPTED", "DOCUMENT_EXISTS"),
                        Set.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)));
                assertEquals(1, context.getBean(KnowledgeMapper.class).countDocuments());
            } finally { executor.shutdownNow(); }
        }
    }

    @Test
    void 拒绝外层事务而不是借用测试提交() {
        try (var context = B2TestSupport.start(directory)) {
            var template = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
            template.executeWithoutResult(status -> assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> context.getBean(DocumentAdmissionService.class)
                            .submit(new SubmitDocumentRequest("outer", "", null))).code()));
            assertEquals(0, context.getBean(KnowledgeMapper.class).countDocuments());
        }
    }

    @Test
    void 真实提交后抛错通过本次UUID确认接收不重复写入() {
        try (var context = B2TestSupport.start(directory)) {
            AtomicBoolean first = new AtomicBoolean(true);
            var manager = new Manager(context.getBean(PlatformTransactionManager.class)) {
                @Override public void commit(TransactionStatus status) {
                    super.commit(status);
                    if (first.compareAndSet(true, false)) { throw new TransactionSystemException("提交结果测试"); }
                }
            };
            var mapper = context.getBean(KnowledgeMapper.class);
            var result = service(context, mapper, manager).submit(new SubmitDocumentRequest("confirmed", "", null));
            assertEquals(result.taskId(), mapper.findDocument("confirmed").taskId());
            assertEquals(1, mapper.countDocuments());
            assertTrue(context.getBean(TaskReadiness.class).accepting());
        }
    }

    private static DocumentAdmissionService service(ConfigurableApplicationContext context, KnowledgeMapper mapper,
                                                     PlatformTransactionManager manager) {
        return new DocumentAdmissionService(mapper, context.getBean(RequestValidator.class),
                context.getBean(VectorProperties.class), context.getBean(TextEmbedder.class),
                context.getBean(TaskReadiness.class), manager);
    }

    private static void await(CountDownLatch latch) {
        try { assertTrue(latch.await(5, TimeUnit.SECONDS)); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); throw new AssertionError(exception); }
    }

    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;
        Manager(PlatformTransactionManager delegate) { this.delegate = delegate; }
        @Override public TransactionStatus getTransaction(TransactionDefinition definition) {
            return delegate.getTransaction(definition);
        }
        @Override public void commit(TransactionStatus status) { delegate.commit(status); }
        @Override public void rollback(TransactionStatus status) { delegate.rollback(status); }
    }
}
