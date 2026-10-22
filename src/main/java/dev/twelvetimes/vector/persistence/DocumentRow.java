package dev.twelvetimes.vector.persistence;

import java.time.Instant;

/** 按查询列顺序构造的只读快照，不作为整行覆盖更新的实体使用。 */
public record DocumentRow(String documentId, String text, String channel, Instant submittedAt,
                          boolean invalidated, Instant invalidatedAt, Integer vectorLength,
                          Instant vectorCompletedAt, long hitCount, String taskId, String status,
                          String embeddingVersion, String runToken, int attemptCount,
                          Instant startedAt, Instant finishedAt, String errorCode, String errorMessage) {
}
