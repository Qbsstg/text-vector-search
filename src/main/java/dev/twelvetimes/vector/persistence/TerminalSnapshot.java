package dev.twelvetimes.vector.persistence;

import java.time.Instant;

/** 独立连接读取的完整终态证据；确认必须比较向量字节，不能只比较长度或任务状态。 */
public record TerminalSnapshot(String taskId, String documentId, String status, String runToken,
                                String taskVersion, Instant finishedAt, String errorCode, String errorMessage,
                                byte[] vectorBytes, String documentVersion, Instant vectorCompletedAt) {
}
