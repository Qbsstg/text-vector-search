package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.embedding.TextEmbedder;

/** 生产处理器仅捕获计算异常；终态持久化异常必须冒泡降级，不能误写成计算FAILED。 */
public final class ProductionTaskExecutionHandler implements TaskExecutionHandler {
    private final TextEmbedder embedder;
    private final TaskTerminalService terminals;

    public ProductionTaskExecutionHandler(TextEmbedder embedder, TaskTerminalService terminals) {
        this.embedder = embedder;
        this.terminals = terminals;
    }

    @Override
    public void handle(TaskExecution execution) {
        if (!embedder.version().equals(execution.embeddingVersion())) {
            terminals.fail(execution, TaskFailure.VERSION_MISMATCH);
            return;
        }
        float[] vector;
        try {
            vector = embedder.embed(execution.text());
        } catch (IllegalArgumentException exception) {
            terminals.fail(execution, TaskFailure.INPUT_INVALID);
            return;
        } catch (RuntimeException exception) {
            terminals.fail(execution, TaskFailure.COMPUTATION_FAILED);
            return;
        }
        terminals.succeed(execution, vector);
    }
}
