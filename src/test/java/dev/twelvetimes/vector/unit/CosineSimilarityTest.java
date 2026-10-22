package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.search.CosineSimilarity;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CosineSimilarityTest {
    @Test
    void unnormalizedVectorsUseTheirActualNorms() {
        float[] left = vector(3, 4);
        float[] right = vector(4, 3);
        assertEquals(0.96, CosineSimilarity.score(left, right), 1e-15);
        assertEquals(1.0, CosineSimilarity.score(left, vector(6, 8)), 1e-15);
        assertEquals(-1.0, CosineSimilarity.score(left, vector(-6, -8)), 1e-15);
        assertEquals(0.0, CosineSimilarity.score(vector(1, 0), vector(0, 2)));
    }

    @Test
    void zeroNormAndSignedZerosHavePositiveZeroScore() {
        for (double score : new double[] {
                CosineSimilarity.score(new float[256], vector(1, 2)),
                CosineSimilarity.score(vector(1, 2), new float[256]),
                CosineSimilarity.score(vector(-0.0f, 0), vector(-1, 0)),
                CosineSimilarity.score(new float[256], new float[256])}) {
            assertEquals(0L, Double.doubleToRawLongBits(score));
        }
    }

    @Test
    void verySmallAndVeryLargeFiniteValuesStayFiniteAndInRange() {
        for (float value : new float[] {Float.MIN_VALUE, Float.MIN_NORMAL, Float.MAX_VALUE}) {
            float[] left = new float[256];
            Arrays.fill(left, value);
            double score = CosineSimilarity.score(left, left);
            assertTrue(Double.isFinite(score));
            assertTrue(score >= -1 && score <= 1);
            assertEquals(1.0, score, 1e-15);
            assertEquals(-1.0, CosineSimilarity.score(vector(value, 0), vector(-value, 0)), 1e-15);
        }
    }

    @Test
    void validationChecksBothCompleteInputsEvenWhenOneNormIsZero() {
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(null, new float[256]));
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(new float[256], null));
        for (int size : new int[] {0, 255, 257}) {
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(new float[size], new float[256]));
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(new float[256], new float[size]));
        }
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] invalid = new float[256];
            invalid[255] = value;
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(invalid, new float[256]));
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(new float[256], invalid));
        }
    }

    @Test
    void scoringDoesNotMutateInputsAndIsSymmetric() {
        float[] left = vector(3, -2);
        float[] right = vector(5, 7);
        float[] originalLeft = left.clone();
        float[] originalRight = right.clone();
        assertEquals(CosineSimilarity.score(left, right), CosineSimilarity.score(right, left));
        assertArrayEquals(originalLeft, left);
        assertArrayEquals(originalRight, right);
    }

    private static float[] vector(float first, float second) {
        float[] vector = new float[256];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
