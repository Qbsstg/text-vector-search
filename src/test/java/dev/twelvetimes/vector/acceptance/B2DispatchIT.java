package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.persistence.DocumentRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskExecution;
import dev.twelvetimes.vector.task.TaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.await;
import static dev.twelvetimes.vector.acceptance.B2QaEnvironment.until;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 处理器为受控测试替身，不回写终态；RUNNING不被当作B3任务完成。 */
class B2DispatchIT {
    @TempDir Path directory;

    @Test
    void 两个独立领取事务仅一个成功且令牌和尝试次数吻合() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("race");
            var barrier = new CyclicBarrier(2);
            var executor = Executors.newFixedThreadPool(2);
            try {
                var first = executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return env.bean(TaskClaimService.class).claim(task);
                });
                var second = executor.submit(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    return env.bean(TaskClaimService.class).claim(task);
                });
                var a = first.get(8, TimeUnit.SECONDS);
                var b = second.get(8, TimeUnit.SECONDS);
                assertEquals(1, (a.isPresent() ? 1 : 0) + (b.isPresent() ? 1 : 0));
                TaskExecution won = a.orElseGet(b::orElseThrow);
                UUID.fromString(won.runToken());
                DocumentRow row = env.bean(KnowledgeMapper.class).findTask(task);
                assertEquals("RUNNING", row.status());
                assertEquals(won.runToken(), row.runToken());
                assertEquals(1, row.attemptCount());
                assertEquals(1, won.attemptCount());
                assertNotNull(row.startedAt());
                assertTrue(env.bean(TaskClaimService.class).claim(task).isEmpty());
                assertEquals(1, env.bean(KnowledgeMapper.class).findTask(task).attemptCount());
            } finally {
                executor.shutdown();
                assertTrue(executor.awaitTermination(8, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void Worker调用处理器前已提交且无事务连接绑定() throws Exception {
        try (var env = new B2QaEnvironment(directory, false, "--spring.datasource.hikari.maximum-pool-size=1")) {
            String task = env.submit("outside");
            CountDownLatch entered = new CountDownLatch(1);
            CountDownLatch release = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            var dispatcher = dispatcher(env, env.bean(KnowledgeMapper.class), env.bean(TaskClaimService.class), execution -> {
                try {
                    assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
                    assertFalse(TransactionSynchronizationManager.hasResource(env.bean(DataSource.class)));
                    var persisted = env.bean(KnowledgeMapper.class).findTask(task);
                    assertEquals("RUNNING", persisted.status());
                    assertEquals(execution.runToken(), persisted.runToken());
                } catch (Throwable error) {
                    failure.set(error);
                } finally {
                    entered.countDown();
                }
                await(release);
            });
            try {
                assertEquals(1, dispatcher.scanOnce());
                await(entered);
                assertEquals(null, failure.get());
                // 连接池仅有一个连接；处理器暂停时仍可完成另一线程写入，证明连接未跨处理保留。
                env.submit("during-handler");
                assertEquals(2, env.bean(KnowledgeMapper.class).countDocuments());
            } finally {
                release.countDown();
                dispatcher.close();
            }
        }
    }

    @Test
    void 一Worker一队列饱和拒绝项保持QUEUED且清标记后补扫() throws Exception {
        try (var env = new B2QaEnvironment(directory, false, "--vector.worker-count=1", "--vector.queue-capacity=1")) {
            String first = env.submit("first");
            List<String> executions = new CopyOnWriteArrayList<>();
            CountDownLatch firstEntered = new CountDownLatch(1);
            CountDownLatch secondEntered = new CountDownLatch(1);
            CountDownLatch thirdEntered = new CountDownLatch(1);
            CountDownLatch releaseFirst = new CountDownLatch(1);
            CountDownLatch releaseSecond = new CountDownLatch(1);
            var dispatcher = dispatcher(env, env.bean(KnowledgeMapper.class), env.bean(TaskClaimService.class), execution -> {
                executions.add(execution.taskId());
                if (executions.size() == 1) {
                    firstEntered.countDown();
                    await(releaseFirst);
                } else if (executions.size() == 2) {
                    secondEntered.countDown();
                    await(releaseSecond);
                } else {
                    thirdEntered.countDown();
                }
            });
            try {
                assertEquals(1, dispatcher.scanOnce());
                await(firstEntered);
                assertEquals(List.of(first), executions);
                String second = env.submit("second");
                String third = env.submit("third");
                // 固定扫描先后，避免相同时间精度下UUID排序影响测试控制顺序。
                env.sql("UPDATE embedding_tasks SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:01+00' WHERE task_id=?", second);
                env.sql("UPDATE embedding_tasks SET submitted_at=TIMESTAMP WITH TIME ZONE '2026-10-22 00:00:02+00' WHERE task_id=?", third);
                assertEquals(1, dispatcher.scanOnce());
                assertEquals(1, dispatcher.queuedCount());
                assertEquals(2, dispatcher.inFlightCount());
                assertEquals("QUEUED", env.bean(KnowledgeMapper.class).findTask(third).status());
                assertEquals(0, env.bean(KnowledgeMapper.class).findTask(third).attemptCount());
                releaseFirst.countDown();
                await(secondEntered);
                until(() -> dispatcher.inFlightCount() == 1);
                assertEquals(1, dispatcher.scanOnce(), "被拒绝项必须能再次入队，不能残留in-flight标记");
                releaseSecond.countDown();
                await(thirdEntered);
                until(() -> dispatcher.inFlightCount() == 0);
                assertEquals(List.of(first, second, third), executions);
                for (String id : executions) {
                    var row = env.bean(KnowledgeMapper.class).findTask(id);
                    assertEquals("RUNNING", row.status());
                    assertEquals(1, row.attemptCount());
                }
            } finally {
                releaseFirst.countDown();
                releaseSecond.countDown();
                dispatcher.close();
            }
        }
    }

    @Test
    void 单次扫描数据库错误不阻止下一次真实扫描() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("scan-error");
            AtomicBoolean failOnce = new AtomicBoolean(true);
            var mapper = env.mapper((name, args, proceed) -> {
                if (name.equals("queued") && failOnce.compareAndSet(true, false)) {
                    throw new DataAccessResourceFailureException("仅测试：一次扫描故障");
                }
                return proceed.call();
            });
            CountDownLatch called = new CountDownLatch(1);
            try (var dispatcher = dispatcher(env, mapper, env.bean(TaskClaimService.class), execution -> called.countDown())) {
                assertEquals(0, dispatcher.scanOnce());
                assertEquals("QUEUED", env.bean(KnowledgeMapper.class).findTask(task).status());
                assertTrue(env.bean(TaskReadiness.class).accepting());
                assertEquals(1, dispatcher.scanOnce());
                await(called);
                until(() -> dispatcher.inFlightCount() == 0);
                assertEquals(1, env.bean(KnowledgeMapper.class).findTask(task).attemptCount());
            }
        }
    }

    @Test
    void 领取物理提交后抛错经本次令牌确认只执行一次() throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("claim-confirmed");
            AtomicBoolean committed = new AtomicBoolean();
            AtomicInteger updates = new AtomicInteger();
            AtomicInteger confirms = new AtomicInteger();
            var mapper = env.mapper((name, args, proceed) -> {
                if (name.equals("claim")) {
                    updates.incrementAndGet();
                }
                if (name.equals("findTask") && committed.get()) {
                    confirms.incrementAndGet();
                }
                return proceed.call();
            });
            var claims = new TaskClaimService(mapper, env.bean(TaskReadiness.class),
                    afterCommit(env, committed, () -> { }));
            AtomicInteger executions = new AtomicInteger();
            AtomicReference<String> token = new AtomicReference<>();
            try (var dispatcher = dispatcher(env, mapper, claims, execution -> {
                token.set(execution.runToken());
                executions.incrementAndGet();
            })) {
                assertEquals(1, dispatcher.scanOnce());
                until(() -> dispatcher.inFlightCount() == 0);
                assertEquals(1, updates.get());
                assertEquals(1, confirms.get());
                assertEquals(1, executions.get());
                assertEquals(env.bean(KnowledgeMapper.class).findTask(task).runToken(), token.get());
                assertEquals(1, env.bean(KnowledgeMapper.class).findTask(task).attemptCount());
                assertEquals(0, dispatcher.scanOnce());
                assertTrue(env.bean(TaskReadiness.class).accepting());
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"读取失败", "令牌不符", "状态不符"})
    void 领取提交无法确认时绝不执行处理器或重领(String mode) throws Exception {
        try (var env = new B2QaEnvironment(directory, false)) {
            String task = env.submit("claim-unknown");
            AtomicBoolean committed = new AtomicBoolean();
            AtomicInteger updates = new AtomicInteger();
            var mapper = env.mapper((name, args, proceed) -> {
                if (name.equals("claim")) {
                    updates.incrementAndGet();
                }
                if (name.equals("findTask") && committed.get() && mode.equals("读取失败")) {
                    throw new DataAccessResourceFailureException("仅测试：确认失败");
                }
                return proceed.call();
            });
            var manager = afterCommit(env, committed, () -> {
                // 只构造确认反例，不代表生产支持在线抢占或状态恢复。
                if (mode.equals("令牌不符")) {
                    env.sql("UPDATE embedding_tasks SET run_token='other-token' WHERE task_id=?", task);
                } else if (mode.equals("状态不符")) {
                    env.sql("UPDATE embedding_tasks SET status='QUEUED' WHERE task_id=?", task);
                }
            });
            var claims = new TaskClaimService(mapper, env.bean(TaskReadiness.class), manager);
            AtomicInteger handled = new AtomicInteger();
            try (var dispatcher = dispatcher(env, mapper, claims, execution -> handled.incrementAndGet())) {
                assertEquals(1, dispatcher.scanOnce());
                until(() -> dispatcher.inFlightCount() == 0);
                assertFalse(env.bean(TaskReadiness.class).accepting());
                assertEquals(0, handled.get());
                assertEquals(1, updates.get());
                assertEquals(1, env.bean(KnowledgeMapper.class).findTask(task).attemptCount());
                assertEquals(0, dispatcher.scanOnce());
                assertEquals(1, updates.get(), "确认未知后不能盲重试条件领取");
                var blocked = org.junit.jupiter.api.Assertions.assertThrows(BusinessException.class, () -> env.submit("blocked"));
                assertEquals("SERVICE_NOT_READY", blocked.code());
            }
        }
    }

    private static TaskDispatcher dispatcher(B2QaEnvironment env, KnowledgeMapper mapper, TaskClaimService claims,
                                              TaskExecutionHandler handler) {
        return new TaskDispatcher(mapper, claims, env.bean(TaskReadiness.class), env.bean(VectorProperties.class), handler);
    }

    @FunctionalInterface
    interface CommittedAction {
        void run() throws Exception;
    }

    /** 先完成真实提交及确认反例准备，最后抛提交异常；不使用会回滚的普通异常冒充未知。 */
    private static B2QaEnvironment.Manager afterCommit(B2QaEnvironment env, AtomicBoolean committed, CommittedAction action) {
        return new B2QaEnvironment.Manager(env.bean(PlatformTransactionManager.class)) {
            @Override
            public void commit(TransactionStatus status) {
                super.commit(status);
                if (committed.compareAndSet(false, true)) {
                    try {
                        action.run();
                    } catch (Exception exception) {
                        throw new AssertionError(exception);
                    }
                    throw new TransactionSystemException("仅测试：领取物理提交后异常");
                }
            }
        };
    }
}
