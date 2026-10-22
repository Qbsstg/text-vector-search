package dev.twelvetimes.vector.task;

/**
 * B3执行端口。实现必须负责计算与可靠终态；B2不提供生产空实现。
 * 调用时领取事务已物理提交，当前线程不持有该事务或连接。
 */
@FunctionalInterface
public interface TaskExecutionHandler {
    void handle(TaskExecution execution) throws Exception;
}
