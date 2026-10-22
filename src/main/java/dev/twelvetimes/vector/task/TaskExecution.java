package dev.twelvetimes.vector.task;

/** 领取已提交后交给处理器的不可变快照；令牌必须用于未来B3终态条件回写。 */
public record TaskExecution(String taskId, String documentId, String runToken, String embeddingVersion,
                             int attemptCount, String text, String channel) {
}
