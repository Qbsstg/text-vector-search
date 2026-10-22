package dev.twelvetimes.vector.search;

import dev.twelvetimes.vector.embedding.TextEmbedder;

/** Exact cosine scoring for finite 256-dimensional vectors, including non-unit input. */
public final class CosineSimilarity {
    private CosineSimilarity() {
    }

    /**
     * Accumulates dot product and both squared norms in fixed index order using double.
     * Casting before multiplication also keeps Float.MAX_VALUE products finite. A zero
     * norm has defined score +0.0, but both complete inputs are still validated first.
     * The final clamp only corrects floating-point roundoff beyond the cosine range.
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
        return result == 0.0 ? 0.0 : result; // Normalize -0.0 so it cannot break Top-K tie semantics.
    }
}
