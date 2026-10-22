package dev.twelvetimes.vector.search;

import dev.twelvetimes.vector.embedding.TextEmbedder;

/** 计算有限值 256 维向量的精确余弦相似度，输入无需事先单位化。 */
public final class CosineSimilarity {
    private CosineSimilarity() {
    }

    /**
     * 按固定下标顺序用 double 累加点积及两个范数平方。
     * 先转换再相乘，保证 Float.MAX_VALUE 的乘积仍为有限值。
     * 零范数得分定义为 +0.0，但仍先校验双方完整输入，不能跳过非法元素。
     * 最后仅对浮点舍入导致的范围越界进行钳制。
     */
    public static double score(float[] left, float[] right) {
        if (left == null || right == null
                || left.length != TextEmbedder.DIMENSIONS || right.length != TextEmbedder.DIMENSIONS) {
            throw new IllegalArgumentException("Both vectors must have exactly 256 dimensions");
        }
        double dot = 0.0;
        double leftSquaredNorm = 0.0;
        double rightSquaredNorm = 0.0;
        for (int index = 0; index < TextEmbedder.DIMENSIONS; index++) {
            double leftValue = left[index];
            double rightValue = right[index];
            if (!Double.isFinite(leftValue) || !Double.isFinite(rightValue)) {
                throw new IllegalArgumentException("Vectors must contain only finite values");
            }
            dot += leftValue * rightValue;
            leftSquaredNorm += leftValue * leftValue;
            rightSquaredNorm += rightValue * rightValue;
        }
        if (leftSquaredNorm == 0.0 || rightSquaredNorm == 0.0) {
            return 0.0;
        }
        double result = dot / (StrictMath.sqrt(leftSquaredNorm) * StrictMath.sqrt(rightSquaredNorm));
        result = StrictMath.max(-1.0, StrictMath.min(1.0, result));
        return result == 0.0 ? 0.0 : result; // 统一负零，避免破坏 Top-K 的同分语义。
    }
}
