package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.b2.B2CompositionTestApplication;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.document.DocumentAdmissionService;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.management.ManagementFactory;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 通过真实文件H2及生产事务组件验证接收；不用测试外层事务代替实际提交。 */
class B2AdmissionIT {
    @TempDir Path directory;

    @Test
    void 两表实际写入后异常使整笔接收回滚() throws Exception {
        try (var context = start("rollback")) {
            AtomicInteger inserted = new AtomicInteger();
            KnowledgeMapper mapper = intercept(context, (name, args, proceed) -> {
                Object result = proceed.call();
                if (name.equals("insertDocument")) {
                    inserted.incrementAndGet();
                }
                if (name.equals("insertTask")) {
                    inserted.incrementAndGet();
                    // 两条SQL都已执行后才抛错，比只在第二次调用前报错更严格。
                    throw new DataAccessResourceFailureException("仅测试：第二表写入后故障");
                }
                return result;
            });
            var exception = assertThrows(BusinessException.class,
                    () -> service(context, mapper, manager(context)).submit(request("rollback", "原文")));
            assertEquals("DATABASE_UNAVAILABLE", exception.code());
            assertEquals(2, inserted.get());
            assertSnapshot(context, 0, 0, 0);
            assertTrue(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 第二线程真实阻塞于接收锁且事务开启晚于首笔物理提交() throws Exception {
        try (var context = start("commit-lock", "--vector.max-pending-tasks=1")) {
            List<String> events = Collections.synchronizedList(new ArrayList<>());
            CountDownLatch atCommit = new CountDownLatch(1);
            CountDownLatch releaseCommit = new CountDownLatch(1);
            AtomicReference<Thread> firstThread = new AtomicReference<>();
            AtomicReference<Thread> secondThread = new AtomicReference<>();
            AtomicInteger begins = new AtomicInteger();
            var wrapped = new Manager(manager(context)) {
                @Override
                public TransactionStatus getTransaction(TransactionDefinition definition) {
                    events.add("BEGIN-" + begins.incrementAndGet());
                    return super.getTransaction(definition);
                }

                @Override
                public void commit(TransactionStatus status) {
                    if (Thread.currentThread() == firstThread.get()) {
                        events.add("COMMIT-1-ENTER");
                        atCommit.countDown();
                        await(releaseCommit);
                        super.commit(status);
                        events.add("COMMIT-1-END");
                    } else {
                        super.commit(status);
                    }
                }
            };
            var admission = service(context, context.getBean(KnowledgeMapper.class), wrapped);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> {
                    firstThread.set(Thread.currentThread());
                    return admission.submit(request("first", "保留原文"));
                });
                assertTrue(atCommit.await(5, TimeUnit.SECONDS));
                assertSnapshot(context, 0, 0, 0);
                var second = executor.submit(() -> {
                    secondThread.set(Thread.currentThread());
                    return assertThrows(BusinessException.class, () -> admission.submit(request("second", "")));
                });
                // 明确观察BLOCKED及锁持有者，不能把“第二线程启动”误当成已经竞争了锁。
                assertBlockedOnFirstThread(secondThread, firstThread.get());
                assertEquals(List.of("BEGIN-1", "COMMIT-1-ENTER"), List.copyOf(events));
                assertEquals(1, begins.get(), "第二请求尚未开启事务");
                releaseCommit.countDown();
                assertEquals("first", first.get(5, TimeUnit.SECONDS).documentId());
                assertEquals("CAPACITY_EXCEEDED", second.get(5, TimeUnit.SECONDS).code());
                assertEquals(List.of("BEGIN-1", "COMMIT-1-ENTER", "COMMIT-1-END", "BEGIN-2"), List.copyOf(events));
                assertSnapshot(context, 1, 1, 1);
            } finally {
                releaseCommit.countDown();
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void 十二个并发重复ID只接收一次并保留获胜原文() throws Exception {
        try (var context = start("duplicates")) {
            var admission = context.getBean(DocumentAdmissionService.class);
            var barrier = new CyclicBarrier(12);
            var executor = Executors.newFixedThreadPool(12);
            List<java.util.concurrent.Future<String>> results = new ArrayList<>();
            try {
                for (int index = 0; index < 12; index++) {
                    String text = "原文-" + index;
                    results.add(executor.submit(() -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        try {
                            var accepted = admission.submit(request("same", text));
                            UUID.fromString(accepted.taskId());
                            return text;
                        } catch (BusinessException exception) {
                            assertEquals(409, exception.status());
                            assertEquals("DOCUMENT_EXISTS", exception.code());
                            return "拒绝";
                        }
                    }));
                }
                List<String> winners = new ArrayList<>();
                for (var result : results) {
                    String value = result.get(10, TimeUnit.SECONDS);
                    if (!value.equals("拒绝")) {
                        winners.add(value);
                    }
                }
                assertEquals(1, winners.size());
                assertEquals(winners.get(0), context.getBean(KnowledgeMapper.class).findDocument("same").text());
                assertSnapshot(context, 1, 1, 1);
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void 文档总量包含已失败已失效数据而不只计算待处理任务() throws Exception {
        try (var context = start("document-limit", "--vector.max-documents=1", "--vector.max-pending-tasks=3")) {
            var admission = context.getBean(DocumentAdmissionService.class);
            admission.submit(request("occupied", "原文"));
            sql(context, "UPDATE embedding_tasks SET status='FAILED'");
            sql(context, "UPDATE documents SET invalidated=TRUE, invalidated_at=CURRENT_TIMESTAMP");
            var exception = assertThrows(BusinessException.class, () -> admission.submit(request("extra", "")));
            assertEquals(429, exception.status());
            assertEquals("CAPACITY_EXCEEDED", exception.code());
            assertEquals(0, context.getBean(KnowledgeMapper.class).countPending());
            assertSnapshot(context, 1, 1, 1);
        }
    }

    @Test
    void 待处理容量包含RUNNING而失效不释放名额() throws Exception {
        try (var context = start("pending-limit", "--vector.max-documents=4", "--vector.max-pending-tasks=1")) {
            var admission = context.getBean(DocumentAdmissionService.class);
            admission.submit(request("occupied", ""));
            sql(context, "UPDATE embedding_tasks SET status='RUNNING', run_token='existing-token', attempt_count=1");
            sql(context, "UPDATE documents SET invalidated=TRUE, invalidated_at=CURRENT_TIMESTAMP");
            assertEquals("CAPACITY_EXCEEDED", assertThrows(BusinessException.class,
                    () -> admission.submit(request("extra", ""))).code());
            assertSnapshot(context, 1, 1, 1);
        }
    }

    @Test
    void 真正提交后抛错必须按本次任务核对且不重复插入() throws Exception {
        try (var context = start("confirmed")) {
            AtomicBoolean committed = new AtomicBoolean();
            AtomicInteger confirmations = new AtomicInteger();
            KnowledgeMapper mapper = intercept(context, (name, args, proceed) -> {
                if (name.equals("findTask")) {
                    assertTrue(committed.get());
                    UUID.fromString((String) args[0]);
                    confirmations.incrementAndGet();
                }
                return proceed.call();
            });
            var wrapped = afterCommitFailure(context, committed);
            var accepted = service(context, mapper, wrapped).submit(request("confirmed", "不重复"));
            assertEquals(1, confirmations.get());
            assertEquals(accepted.taskId(), context.getBean(KnowledgeMapper.class).findDocument("confirmed").taskId());
            assertEquals(context.getBean(TextEmbedder.class).version(),
                    context.getBean(KnowledgeMapper.class).findDocument("confirmed").embeddingVersion());
            assertSnapshot(context, 1, 1, 1);
            assertTrue(context.getBean(TaskReadiness.class).accepting());
        }
    }

    @Test
    void 实际提交但确认读取失败时保留记录并关闭后续接收() throws Exception {
        try (var context = start("unknown")) {
            AtomicBoolean committed = new AtomicBoolean();
            KnowledgeMapper mapper = intercept(context, (name, args, proceed) -> {
                if (committed.get() && name.equals("findTask")) {
                    throw new DataAccessResourceFailureException("仅测试：确认连接不可用");
                }
                return proceed.call();
            });
            var admission = service(context, mapper, afterCommitFailure(context, committed));
            var unknown = assertThrows(BusinessException.class, () -> admission.submit(request("unknown", "仍已提交")));
            assertEquals(503, unknown.status());
            assertEquals("RESULT_UNKNOWN", unknown.code());
            assertSnapshot(context, 1, 1, 1);
            assertNotNull(context.getBean(KnowledgeMapper.class).findDocument("unknown"));
            assertFalse(context.getBean(TaskReadiness.class).accepting());
            assertNotNull(context.getBean(TaskReadiness.class).reason());
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> admission.submit(request("later", ""))).code());
            assertSnapshot(context, 1, 1, 1);
        }
    }

    @Test
    void 调用者外层事务被明确拒绝且不会借用其提交() throws Exception {
        try (var context = start("outer")) {
            var template = new TransactionTemplate(manager(context));
            template.executeWithoutResult(status -> {
                var failure = assertThrows(BusinessException.class,
                        () -> context.getBean(DocumentAdmissionService.class).submit(request("outer", "")));
                assertEquals("SERVICE_NOT_READY", failure.code());
            });
            assertSnapshot(context, 0, 0, 0);
        }
    }

    @Test
    void 同库重启发现旧RUNNING时拒绝接收但不假恢复() throws Exception {
        String taskId;
        try (var context = start("old-running")) {
            taskId = context.getBean(DocumentAdmissionService.class).submit(request("old", "待恢复")).taskId();
            sql(context, "UPDATE embedding_tasks SET status='RUNNING',run_token='old-token',attempt_count=7");
        }
        try (var context = start("old-running")) {
            assertFalse(context.getBean(TaskReadiness.class).accepting());
            assertEquals("SERVICE_NOT_READY", assertThrows(BusinessException.class,
                    () -> context.getBean(DocumentAdmissionService.class).submit(request("new", ""))).code());
            var old = context.getBean(KnowledgeMapper.class).findTask(taskId);
            assertEquals("RUNNING", old.status());
            assertEquals("old-token", old.runToken());
            assertEquals(7, old.attemptCount());
            assertSnapshot(context, 1, 1, 1);
        }
    }

    /** 观察监视器拥有者和阻塞栈，使提交前的竞争证据不依赖调度时机或sleep。 */
    private static void assertBlockedOnFirstThread(AtomicReference<Thread> contender, Thread owner) {
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        var threads = ManagementFactory.getThreadMXBean();
        while (System.nanoTime() < deadline) {
            Thread thread = contender.get();
            if (thread != null) {
                var info = threads.getThreadInfo(thread.getId(), 16);
                if (info != null && info.getThreadState() == Thread.State.BLOCKED
                        && info.getLockOwnerId() == owner.getId()) {
                    assertTrue(List.of(info.getStackTrace()).stream().anyMatch(frame ->
                            frame.getClassName().equals(DocumentAdmissionService.class.getName())
                                    && frame.getMethodName().equals("submit")));
                    return;
                }
            }
            Thread.yield();
        }
        fail("未观察到第二线程真实阻塞于首线程持有的接收锁");
    }

    private ConfigurableApplicationContext start(String database, String... overrides) {
        var args = new ArrayList<>(List.of("--spring.main.web-application-type=none",
                "--spring.main.banner-mode=off", "--logging.level.root=WARN",
                "--spring.datasource.url=jdbc:h2:file:" + directory.resolve(database + "/knowledge").toAbsolutePath()
                        + ";WRITE_DELAY=0", "--spring.datasource.username=sa", "--spring.datasource.password="));
        args.addAll(List.of(overrides));
        return new SpringApplication(B2CompositionTestApplication.class).run(args.toArray(String[]::new));
    }

    /** 每次均借用新的数据源连接，直接检查两表与关联数，不只依赖JOIN映射掩盖孤立行。 */
    private static void assertSnapshot(ConfigurableApplicationContext context, int documents, int tasks, int joined)
            throws Exception {
        try (var connection = context.getBean(DataSource.class).getConnection();
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT (SELECT COUNT(*) FROM documents),"
                     + "(SELECT COUNT(*) FROM embedding_tasks),"
                     + "(SELECT COUNT(*) FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id)")) {
            assertTrue(row.next());
            assertEquals(documents, row.getInt(1));
            assertEquals(tasks, row.getInt(2));
            assertEquals(joined, row.getInt(3));
        }
    }

    private static void sql(ConfigurableApplicationContext context, String sql) throws Exception {
        try (var connection = context.getBean(DataSource.class).getConnection();
             var statement = connection.createStatement()) {
            statement.executeUpdate(sql);
        }
    }

    private static SubmitDocumentRequest request(String id, String text) {
        return new SubmitDocumentRequest(id, text, null);
    }

    private static PlatformTransactionManager manager(ConfigurableApplicationContext context) {
        return context.getBean(PlatformTransactionManager.class);
    }

    private static DocumentAdmissionService service(ConfigurableApplicationContext context, KnowledgeMapper mapper,
                                                     PlatformTransactionManager manager) {
        return new DocumentAdmissionService(mapper, context.getBean(RequestValidator.class),
                context.getBean(VectorProperties.class), context.getBean(TextEmbedder.class),
                context.getBean(TaskReadiness.class), manager);
    }

    /** 仅首次真实提交后抛错；确认事务仍使用同一个真实事务管理器并正常完成。 */
    private static Manager afterCommitFailure(ConfigurableApplicationContext context, AtomicBoolean committed) {
        return new Manager(manager(context)) {
            @Override
            public void commit(TransactionStatus status) {
                super.commit(status);
                if (committed.compareAndSet(false, true)) {
                    throw new TransactionSystemException("仅测试：物理提交后返回异常");
                }
            }
        };
    }

    @FunctionalInterface
    interface Invocation {
        Object call() throws Throwable;
    }

    @FunctionalInterface
    interface Interceptor {
        Object invoke(String method, Object[] args, Invocation proceed) throws Throwable;
    }

    private static KnowledgeMapper intercept(ConfigurableApplicationContext context, Interceptor interceptor) {
        KnowledgeMapper actual = context.getBean(KnowledgeMapper.class);
        return (KnowledgeMapper) Proxy.newProxyInstance(KnowledgeMapper.class.getClassLoader(),
                new Class<?>[]{KnowledgeMapper.class}, (proxy, method, args) -> interceptor.invoke(method.getName(), args, () -> {
                    try {
                        return method.invoke(actual, args);
                    } catch (InvocationTargetException exception) {
                        throw exception.getCause();
                    }
                }));
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
    }

    /** 装饰真实事务管理器，所有开启、提交与回滚仍落到生产事务组件。 */
    private static class Manager implements PlatformTransactionManager {
        private final PlatformTransactionManager delegate;

        Manager(PlatformTransactionManager delegate) {
            this.delegate = delegate;
        }

        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return delegate.getTransaction(definition);
        }

        @Override
        public void commit(TransactionStatus status) {
            delegate.commit(status);
        }

        @Override
        public void rollback(TransactionStatus status) {
            delegate.rollback(status);
        }
    }
}
