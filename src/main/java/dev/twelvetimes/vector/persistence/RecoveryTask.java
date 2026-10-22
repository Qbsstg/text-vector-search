package dev.twelvetimes.vector.persistence;

import java.time.Instant;

/** 恢复前尝试元信息，用于未知提交后的独立核对，不保存或覆盖文档实体。 */
public record RecoveryTask(String taskId, int attemptCount, String embeddingVersion, Instant submittedAt) {
}
