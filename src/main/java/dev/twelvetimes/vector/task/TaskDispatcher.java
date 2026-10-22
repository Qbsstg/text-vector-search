package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
        synchronized (this) {
            if (closed) { return; }
            closed = true;
            running = false;
            scanner.shutdown();
            workers.shutdown();
        }
        // B2仅停止自身基础设施，不抢占任务、不强制中断H2；完整业务关闭验收仍属B3。
        try {
            if (!workers.awaitTermination(properties.shutdownWaitSeconds(), TimeUnit.SECONDS)) {
                readiness.block("任务停止等待超时");
                LOG.error("任务停止等待超时，保留执行现场待诊断");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            readiness.block("任务停止等待被中断");
        }
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
