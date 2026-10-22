package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.search.ScoredDocument;
import dev.twelvetimes.vector.search.TopKSelector;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TopKSelectorTest {
    @Test
    void tieAtHeapBoundaryKeepsSmallestAsciiIdsRegardlessOfArrivalOrder() {
        List<ScoredDocument> input = List.of(new ScoredDocument("z", 0.5), new ScoredDocument("b", 0.5),
                new ScoredDocument("a", 0.5), new ScoredDocument("A", 0.5), new ScoredDocument("0", 0.5));
        assertEquals(List.of("0", "A", "a"), ids(TopKSelector.select(input, 3)));
        assertEquals(List.of("0"), ids(TopKSelector.select(input, 1)));
    }

    @Test
    void zeroAndNegativeScoresAreKeptAndSignedZeroTiesUseId() {
        List<ScoredDocument> input = List.of(new ScoredDocument("z", -0.0), new ScoredDocument("a", 0.0),
                new ScoredDocument("negative", -1), new ScoredDocument("positive", 1));
        assertEquals(List.of("positive", "a", "z", "negative"), ids(TopKSelector.select(input, 8)));
        assertEquals(0L, Double.doubleToRawLongBits(new ScoredDocument("zero", -0.0).score()));
    }

    @Test
    void heapMatchesIndependentFullSortAcrossDeterministicShufflesAndKValues() {
        List<ScoredDocument> candidates = new ArrayList<>();
        Random random = new Random(7654321L);
        for (int index = 0; index < 250; index++) {
            candidates.add(new ScoredDocument("doc-" + index, (random.nextInt(21) - 10) / 10.0));
        }
        List<ScoredDocument> expected = new ArrayList<>(candidates);
        // 参考实现独立写出完整排序规则，避免复用生产比较器掩盖同一错误。
        expected.sort((left, right) -> left.score() == right.score()
                ? left.documentId().compareTo(right.documentId())
                : left.score() > right.score() ? -1 : 1);
        for (int iteration = 0; iteration < 15; iteration++) {
            Collections.shuffle(candidates, random);
            for (int k : new int[] {1, 2, 17, 100, 250, 300, Integer.MAX_VALUE}) {
                assertEquals(expected.subList(0, Math.min(k, expected.size())), TopKSelector.select(candidates, k));
            }
        }
    }

    @Test
    void hugeKAndEmptyInputDoNotPreallocateKSlots() {
        assertEquals(List.of(), TopKSelector.select(List.of(), Integer.MAX_VALUE));
        assertEquals(List.of(new ScoredDocument("one", -0.5)),
                TopKSelector.select(List.of(new ScoredDocument("one", -0.5)), Integer.MAX_VALUE));
    }

    @Test
    void visitsIterableOnlyOnceAndReturnsImmutableResultsWithoutMutatingInput() {
        List<ScoredDocument> input = new ArrayList<>(List.of(new ScoredDocument("b", -1), new ScoredDocument("a", 1)));
        List<ScoredDocument> original = List.copyOf(input);
        AtomicInteger iterations = new AtomicInteger();
        Iterable<ScoredDocument> once = () -> {
            assertEquals(1, iterations.incrementAndGet());
            return input.iterator();
        };
        List<ScoredDocument> result = TopKSelector.select(once, 1);
        assertEquals(original, input);
        assertThrows(UnsupportedOperationException.class, () -> result.add(new ScoredDocument("c", 0)));
    }

    @Test
    void rejectsInvalidKNullCollectionAndNullElements() {
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(null, 1));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(List.of(), 0));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(List.of(), -1));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(Arrays.asList((ScoredDocument) null), 1));
    }

    @Test
    void scoreBoundaryRejectsNonFiniteOutOfRangeAndInvalidAsciiIds() {
        for (double score : new double[] {Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.01, 1.01}) {
            assertThrows(IllegalArgumentException.class, () -> new ScoredDocument("valid", score));
        }
        for (String id : new String[] {null, "", ".", "_a", " a", "a ", "a/b", "a\\b", "中文", "a".repeat(129)}) {
            assertThrows(IllegalArgumentException.class, () -> new ScoredDocument(id, 0));
        }
        assertEquals("A._-09", new ScoredDocument("A._-09", 0).documentId());
        assertEquals(128, new ScoredDocument("a".repeat(128), 0).documentId().length());
    }

    private static List<String> ids(List<ScoredDocument> scores) {
        return scores.stream().map(ScoredDocument::documentId).toList();
    }
}
