package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.stereotype.Component;

/** B2只保留门禁与诊断原因，既不重置旧RUNNING，也不伪装B3恢复已完成。 */
@Component
public final class TaskReadiness implements SmartInitializingSingleton {
    private final KnowledgeMapper mapper;
    private final AtomicReference<String> reason = new AtomicReference<>();

    public TaskReadiness(KnowledgeMapper mapper) { this.mapper = mapper; }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            if (mapper.countRunning() > 0) { block("存在待B3恢复的RUNNING任务"); }
        } catch (RuntimeException exception) {
            block("启动时无法确认任务状态");
        }
    }

    public void requireAccepting() {
        if (reason.get() != null) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "任务接收尚未就绪");
        }
    }

    public boolean accepting() { return reason.get() == null; }
    public String reason() { return reason.get(); }
    public void block(String value) { reason.compareAndSet(null, value); }
}
