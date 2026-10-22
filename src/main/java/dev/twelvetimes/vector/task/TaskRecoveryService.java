package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.RecoveryTask;
import dev.twelvetimes.vector.persistence.TaskRecoveryMapper;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 所有单例创建后、生命周期调度开始前恢复；失败维持DEGRADED，不先READY再异步查库。 */
public final class TaskRecoveryService implements SmartInitializingSingleton {
    private final TaskRecoveryMapper recovery;
    private final KnowledgeMapper tasks;
    private final TaskReadiness readiness;
    private final TransactionTemplate transaction;

    public TaskRecoveryService(TaskRecoveryMapper recovery, KnowledgeMapper tasks,
                                TaskReadiness readiness, PlatformTransactionManager manager) {
        this.recovery = recovery;
        this.tasks = tasks;
        this.readiness = readiness;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override public void afterSingletonsInstantiated() { recover(); }

    public int recover() {
        if (readiness.state() != TaskReadiness.State.RECOVERING) {
            throw new IllegalStateException("恢复仅能在RECOVERING阶段执行");
        }
        AtomicReference<List<RecoveryTask>> before = new AtomicReference<>(List.of());
        try (var lease = readiness.writeBarrier().enter()) {
            if (lease == null) { return 0; }
            for (int attempt = 0; attempt < 3; attempt++) {
                AtomicBoolean commitAttempted = new AtomicBoolean();
                try {
                    transaction.executeWithoutResult(status -> {
                        before.set(recovery.running());
                        if (recovery.requeueRunning() != before.get().size()) {
                            throw new IllegalStateException("恢复受影响行数异常");
                        }
                        commitAttempted.set(true);
                    });
                } catch (RuntimeException exception) {
                    if (!commitAttempted.get() && exception instanceof TransientDataAccessException && attempt < 2) {
                        if (!pause(attempt)) { return 0; }
                        continue;
                    }
                    if (!commitAttempted.get() || !confirmed(before.get())) {
                        readiness.block("启动任务恢复无法可靠确认");
                        return 0;
                    }
                }
                readiness.ready();
                return before.get().size();
            }
            readiness.block("启动恢复重试次数已用尽");
            return 0;
        }
    }

    private boolean confirmed(List<RecoveryTask> before) {
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                return Boolean.TRUE.equals(transaction.execute(status -> {
                    if (tasks.countRunning() != 0) { return false; }
                    for (RecoveryTask old : before) {
                        var row = tasks.findTask(old.taskId());
                        if (row == null || !"QUEUED".equals(row.status()) || row.runToken() != null
                                || row.startedAt() != null || row.finishedAt() != null || row.errorCode() != null || row.errorMessage() != null
                                || row.attemptCount() != old.attemptCount() || !row.embeddingVersion().equals(old.embeddingVersion())
                                || !old.submittedAt().equals(recovery.taskSubmittedAt(old.taskId()))) { return false; }
                    }
                    return true;
                }));
            } catch (RuntimeException ignored) {
                // 只重试独立确认，不因未知提交再次重置任务。
                if (attempt < 2 && !pause(attempt)) { return false; }
            }
        }
        return false;
    }

    private boolean pause(int attempt) {
        try { Thread.sleep(attempt == 0 ? 50 : 100); return true; }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            readiness.block("启动恢复退避被中断");
            return false;
        }
    }
}
