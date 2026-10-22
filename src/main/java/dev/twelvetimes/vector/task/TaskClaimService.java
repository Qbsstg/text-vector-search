package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.persistence.DocumentRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 数据库条件更新才授予执行权，在途集合不参与领取资格判断。 */
@Service
public final class TaskClaimService {
    private final KnowledgeMapper mapper;
    private final TaskReadiness readiness;
    private final TransactionTemplate transaction;

    public TaskClaimService(KnowledgeMapper mapper, TaskReadiness readiness, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.readiness = readiness;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Optional<TaskExecution> claim(String taskId) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "领取不能嵌入外层事务");
        }
        try (var lease = readiness.enterWrite()) {
            String token = UUID.randomUUID().toString();
            AtomicBoolean commitAttempted = new AtomicBoolean();
            try {
                TaskExecution result = transaction.execute(status -> {
                    if (mapper.claim(taskId, token, Instant.now()) != 1) { return null; }
                    DocumentRow row = mapper.findTask(taskId);
                    if (row == null) { throw new IllegalStateException("领取后任务消失"); }
                    TaskExecution execution = execution(row);
                    commitAttempted.set(true);
                    return execution;
                });
                return Optional.ofNullable(result);
            } catch (RuntimeException exception) {
                if (commitAttempted.get() || exception instanceof TransactionException) {
                    // 必须独立读取并匹配本次token；只见RUNNING不能证明当前线程拥有执行权。
                    try {
                        DocumentRow confirmed = transaction.execute(status -> mapper.findTask(taskId));
                        if (confirmed != null && "RUNNING".equals(confirmed.status()) && token.equals(confirmed.runToken())) {
                            return Optional.of(execution(confirmed));
                        }
                    } catch (RuntimeException ignored) {
                        // 无法确认时保持原数据并停止接收，等待B3诊断，绝不再次条件领取或计算。
                    }
                    readiness.block("领取提交结果无法确认");
                    throw new BusinessException(503, "RESULT_UNKNOWN", "无法确认任务执行权");
                }
                throw exception;
            }
        }
    }

    private static TaskExecution execution(DocumentRow row) {
        return new TaskExecution(row.taskId(), row.documentId(), row.runToken(), row.embeddingVersion(),
                row.attemptCount(), row.text(), row.channel());
    }
}
