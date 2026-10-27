package dev.twelvetimes.vector.persistence;

import java.time.Instant;

/** 单条详情查询的只读快照；版本与原始向量来自文档行，不借用任务版本。 */
public record DocumentDetailRow(String documentId, String text, String channel, Instant submittedAt,
                                boolean invalidated, Instant invalidatedAt, Instant vectorCompletedAt,
                                long hitCount, String taskId, String status, String embeddingVersion,
                                byte[] vectorBytes) {
}
