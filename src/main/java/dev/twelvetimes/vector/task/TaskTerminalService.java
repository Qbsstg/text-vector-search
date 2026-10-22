package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.persistence.TerminalSnapshot;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 终态两表使用独立物理事务；计算结果在进入写许可之前准备，关闭后迟到CPU不会触及数据库。 */
public final class TaskTerminalService {
    public enum TerminalResult { APPLIED, ALREADY_APPLIED, STALE, WRITE_CLOSED }
    private final TaskTerminalMapper mapper;
    private final TaskReadiness readiness;
    private final TaskWriteBarrier barrier;
    private final TransactionTemplate transaction;

    public TaskTerminalService(TaskTerminalMapper mapper, TaskReadiness readiness, TaskWriteBarrier barrier,
                                PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.readiness = readiness;
        this.barrier = barrier;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public TerminalResult succeed(TaskExecution execution, float[] vector) {
        return finish(execution, VectorCodec.encode(vector), null);
    }

    public TerminalResult fail(TaskExecution execution, TaskFailure failure) {
        return finish(execution, null, Objects.requireNonNull(failure));
    }

    private TerminalResult finish(TaskExecution execution, byte[] bytes, TaskFailure failure) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("终态写入不能嵌入外层事务");
        }
        try (var lease = barrier.enter()) {
            if (lease == null) { return TerminalResult.WRITE_CLOSED; }
            for (int attempt = 0; attempt < 3; attempt++) {
                AtomicBoolean commitAttempted = new AtomicBoolean();
                try {
                    return transaction.execute(status -> {
                        Instant now = Instant.now();
                        int rows = failure == null
                                ? mapper.succeed(execution.taskId(), execution.documentId(), execution.runToken(), execution.embeddingVersion(), now)
                                : mapper.fail(execution.taskId(), execution.documentId(), execution.runToken(), execution.embeddingVersion(), now,
                                        failure.code(), failure.message());
                        if (rows == 0) {
                            TerminalSnapshot snapshot = mapper.snapshot(execution.taskId());
                            if (matches(snapshot, execution, bytes, failure)) { return TerminalResult.ALREADY_APPLIED; }
                            if (sameExpectedTerminal(snapshot, execution, failure)) {
                                throw new IllegalStateException("同令牌终态内容不一致");
                            }
                            return TerminalResult.STALE;
                        }
                        if (rows != 1 || (failure == null && mapper.writeVector(execution.documentId(), bytes,
                                execution.embeddingVersion(), now) != 1)) {
                            throw new IllegalStateException("终态写入受影响行数异常");
                        }
                        commitAttempted.set(true);
                        return TerminalResult.APPLIED;
                    });
                } catch (RuntimeException exception) {
                    if (commitAttempted.get() || exception instanceof TransactionException) {
                        // 不能因提交异常重复执行写入，先用新事务比对本次令牌与完整终态/向量。
                        for (int confirmation = 0; confirmation < 3; confirmation++) {
                            try {
                                TerminalSnapshot snapshot = transaction.execute(status -> mapper.snapshot(execution.taskId()));
                                if (matches(snapshot, execution, bytes, failure)) { return TerminalResult.ALREADY_APPLIED; }
                                if (snapshot != null && !Objects.equals(snapshot.runToken(), execution.runToken())) {
                                    return TerminalResult.STALE;
                                }
                                break;
                            } catch (RuntimeException ignored) {
                                if (confirmation < 2) { pause(confirmation); }
                            }
                        }
                        throw degraded("终态提交结果无法确认");
                    }
                    // 只有回调失败且物理回滚已完成的暂时数据库异常允许重试，最多三次。
                    if (exception instanceof TransientDataAccessException && attempt < 2) {
                        pause(attempt);
                        continue;
                    }
                    throw degraded("终态事务无法可靠完成");
                }
            }
            throw degraded("终态重试次数已用尽");
        }
    }

    private static boolean sameExpectedTerminal(TerminalSnapshot snapshot, TaskExecution execution, TaskFailure failure) {
        return snapshot != null && Objects.equals(snapshot.runToken(), execution.runToken())
                && (failure == null ? "SUCCEEDED" : "FAILED").equals(snapshot.status());
    }

    private static boolean matches(TerminalSnapshot snapshot, TaskExecution execution, byte[] bytes, TaskFailure failure) {
        if (!sameExpectedTerminal(snapshot, execution, failure) || !execution.documentId().equals(snapshot.documentId())
                || !execution.embeddingVersion().equals(snapshot.taskVersion()) || snapshot.finishedAt() == null) { return false; }
        if (failure != null) {
            return failure.code().equals(snapshot.errorCode()) && failure.message().equals(snapshot.errorMessage());
        }
        return snapshot.errorCode() == null && snapshot.errorMessage() == null
                && execution.embeddingVersion().equals(snapshot.documentVersion())
                && snapshot.finishedAt().equals(snapshot.vectorCompletedAt()) && Arrays.equals(bytes, snapshot.vectorBytes());
    }

    private BusinessException degraded(String reason) {
        readiness.block(reason);
        return new BusinessException(503, "RESULT_UNKNOWN", "任务终态无法确认，已暂停任务服务");
    }

    private void pause(int attempt) {
        try { Thread.sleep(attempt == 0 ? 50 : 100); }
        catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw degraded("终态数据库退避被中断");
        }
    }
}
