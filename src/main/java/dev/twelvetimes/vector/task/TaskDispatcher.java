package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

/**
 * 持久任务表是事实来源，内存队列只缓冲ID。没有B3处理器时不启动扫描、不领取。
 * 直接构造后可调用scanOnce控制测试时序；只有Spring生命周期start才启用周期扫描。
 */
public final class TaskDispatcher implements SmartLifecycle, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(TaskDispatcher.class);
    private final KnowledgeMapper mapper;
    private final TaskClaimService claims;
    private final TaskReadiness readiness;
    private final VectorProperties properties;
    private final TaskExecutionHandler handler;
    private final Set<String> inFlight = ConcurrentHashMap.newKeySet();
    private final ThreadPoolExecutor workers;
    private final ScheduledExecutorService scanner;
    private final CountDownLatch stopCompleted = new CountDownLatch(1);
    private volatile boolean running;
    private volatile boolean closed;

    public TaskDispatcher(KnowledgeMapper mapper, TaskClaimService claims, TaskReadiness readiness,
                           VectorProperties properties, TaskExecutionHandler handler) {
        this.mapper = mapper;
        this.claims = claims;
        this.readiness = readiness;
        this.properties = properties;
        this.handler = handler;
        workers = new ThreadPoolExecutor(properties.workerCount(), properties.workerCount(), 0L, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(properties.queueCapacity()), threads("task-worker-"),
                new ThreadPoolExecutor.AbortPolicy());
        scanner = Executors.newSingleThreadScheduledExecutor(threads("task-scanner-"));
    }

    @Override
    public synchronized void start() {
        if (running || closed) { return; }
        running = true;
        if (handler != null) {
            scanner.scheduleWithFixedDelay(this::scanOnce, 0, properties.scanIntervalMs(), TimeUnit.MILLISECONDS);
        }
    }

    /** 一次小批扫描，队列拒绝只清本地标记，QUEUED保留供未来扫描补派。 */
    public synchronized int scanOnce() {
        if (handler == null || closed || !readiness.accepting()) { return 0; }
        int dispatched = 0;
        try {
            for (String taskId : mapper.queued(properties.scanBatchSize())) {
                if (!inFlight.add(taskId)) { continue; }
                try {
                    // Runnable只携带任务ID，不缓存原文/旧实体，执行时才做真实条件领取。
                    workers.execute(() -> execute(taskId));
                    dispatched++;
                } catch (RejectedExecutionException exception) {
                    inFlight.remove(taskId);
                    break;
                }
            }
        } catch (RuntimeException exception) {
            // 不让单次数据库异常杀死周期扫描，也不在日志泄露SQL或用户原文。
            LOG.warn("本次任务扫描失败，后续周期将重试：{}", exception.getClass().getSimpleName());
        }
        return dispatched;
    }

    private void execute(String taskId) {
        boolean owned = false;
        try {
            if (closed || !readiness.accepting()) { return; }
            var execution = claims.claim(taskId);
            if (execution.isPresent()) {
                owned = true;
                handler.handle(execution.get());
            }
        } catch (Exception exception) {
            // 处理器越界失败不能假写终态或只留无解释RUNNING；门禁原因保留供B3诊断。
            if (owned) {
                readiness.block("处理器异常，需要诊断");
                LOG.error("任务执行暂停，需要诊断：{}", exception.getClass().getSimpleName());
            } else {
                LOG.warn("本次领取未执行，保留持久状态：{}", exception.getClass().getSimpleName());
            }
        } finally {
            inFlight.remove(taskId);
        }
    }

    public int queuedCount() { return workers.getQueue().size(); }
    public int inFlightCount() { return inFlight.size(); }

    @Override public boolean isRunning() { return running; }
    @Override public void stop() { close(); }
    @Override public int getPhase() { return Integer.MAX_VALUE; }

    @Override
    public void close() {
        boolean owner;
        synchronized (this) {
            owner = !closed;
            if (owner) {
                // 先拒绝新的服务写入，再停止派发；已持短事务许可的请求可以完成物理提交。
                readiness.stopping();
                closed = true;
                running = false;
                scanner.shutdown();
                workers.shutdown();
            }
        }
        if (!owner) {
            awaitStopCompleted();
            return;
        }
        try {
            if (!workers.awaitTermination(properties.shutdownWaitSeconds(), TimeUnit.SECONDS)) {
                LOG.warn("CPU任务停止等待超时，即将关闭回写许可并保留RUNNING");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            LOG.warn("停止等待被中断，仍需排空已有数据库短事务");
        } finally {
            try {
                // CPU不持此许可。超时后的CPU即使晚于数据源销毁返回，也只能得到WRITE_CLOSED。
                // 排空已入短事务是数据安全屏障，不宣称底层数据库卡死时仍能在30秒内完成。
                readiness.writeBarrier().closeAndDrain();
                readiness.stopped();
            } finally {
                stopCompleted.countDown();
            }
        }
    }

    private void awaitStopCompleted() {
        boolean interrupted = false;
        while (true) {
            try { stopCompleted.await(); break; }
            catch (InterruptedException exception) { interrupted = true; }
        }
        if (interrupted) { Thread.currentThread().interrupt(); }
    }

    private static ThreadFactory threads(String prefix) {
        AtomicInteger sequence = new AtomicInteger();
        return task -> {
            Thread thread = new Thread(task, prefix + sequence.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }
}
