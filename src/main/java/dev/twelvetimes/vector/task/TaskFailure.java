package dev.twelvetimes.vector.task;

/** 错误码和固定消息均不携带异常原文、用户文本、路径或SQL。 */
public record TaskFailure(String code, String message) {
    public static final TaskFailure VERSION_MISMATCH = new TaskFailure("VERSION_MISMATCH", "任务算法版本与当前运行版本不一致");
    public static final TaskFailure INPUT_INVALID = new TaskFailure("INPUT_INVALID", "任务原文不符合向量输入约束");
    public static final TaskFailure COMPUTATION_FAILED = new TaskFailure("COMPUTATION_FAILED", "任务向量计算失败");

    public TaskFailure {
        if (code == null || !code.matches("[A-Z][A-Z0-9_]{0,63}") || message == null || message.length() > 512) {
            throw new IllegalArgumentException("任务错误分类格式非法");
        }
    }
}
