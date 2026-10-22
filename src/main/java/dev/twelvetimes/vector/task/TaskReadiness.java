package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** 任务服务就绪状态；先恢复再READY，停止后不可重新接收，降级原因不泄露数据库细节。 */
@Component
public final class TaskReadiness implements SmartInitializingSingleton {
    private static final Logger LOG = LoggerFactory.getLogger(TaskReadiness.class);
    public enum State { RECOVERING, READY, DEGRADED, STOPPING, STOPPED }
    private record Snapshot(State state, String reason) { }
    private final KnowledgeMapper mapper;
    private final ObjectProvider<TaskRecoveryService> recovery;
    private final TaskWriteBarrier barrier = new TaskWriteBarrier();
    private final AtomicReference<Snapshot> current = new AtomicReference<>(new Snapshot(State.RECOVERING, "正在恢复持久任务"));

    public TaskReadiness(KnowledgeMapper mapper) { this(mapper, null); }

    @Autowired
    public TaskReadiness(KnowledgeMapper mapper, ObjectProvider<TaskRecoveryService> recovery) {
        this.mapper = mapper;
        this.recovery = recovery;
    }

    @Override
    public void afterSingletonsInstantiated() {
        // 显式B2测试组成没有恢复组件，保留当时的无处理器/旧RUNNING拒接边界。
        if (recovery != null && recovery.getIfAvailable() != null) { return; }
        try {
            if (mapper.countRunning() > 0) { block("存在待B3恢复的RUNNING任务"); }
            else { ready(); }
        } catch (RuntimeException exception) {
            block("启动时无法确认任务状态");
        }
    }

    public void requireAccepting() {
        if (!accepting()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "任务接收尚未就绪");
        }
    }

    public boolean accepting() { return state() == State.READY; }
    public State state() { return current.get().state(); }
    public String reason() { return current.get().reason(); }
    public TaskWriteBarrier writeBarrier() { return barrier; }

    /** 服务级短写入许可，取得后重查状态，覆盖已经通过HTTP门禁却与停止交错的请求。 */
    public TaskWriteBarrier.Lease enterWrite() {
        requireAccepting();
        TaskWriteBarrier.Lease lease = barrier.enter();
        if (lease == null) { throw new BusinessException(503, "SERVICE_NOT_READY", "任务写入已停止"); }
        try { requireAccepting(); return lease; }
        catch (RuntimeException exception) { lease.close(); throw exception; }
    }

    public boolean ready() {
        Snapshot snapshot = current.get();
        if (snapshot.state() != State.RECOVERING || !barrier.isOpen()) { return false; }
        return current.compareAndSet(snapshot, new Snapshot(State.READY, null));
    }

    public void block(String value) {
        while (true) {
            Snapshot snapshot = current.get();
            if (snapshot.state() == State.DEGRADED || snapshot.state() == State.STOPPING || snapshot.state() == State.STOPPED) { return; }
            if (current.compareAndSet(snapshot, new Snapshot(State.DEGRADED, value))) {
                LOG.warn("任务接收门禁关闭：{}", value);
                return;
            }
        }
    }

    public void stopping() {
        current.updateAndGet(snapshot -> snapshot.state() == State.STOPPED ? snapshot : new Snapshot(State.STOPPING, "任务服务正在停止"));
    }

    public void stopped() {
        if (state() == State.STOPPED) { return; }
        if (state() != State.STOPPING) { throw new IllegalStateException("必须先停止接收再结束任务服务"); }
        current.set(new Snapshot(State.STOPPED, "任务服务已停止"));
    }
}
