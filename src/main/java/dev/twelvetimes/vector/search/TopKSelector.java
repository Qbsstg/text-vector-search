package dev.twelvetimes.vector.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** Exact bounded-heap selection; eligibility filtering and unique IDs are caller preconditions. */
public final class TopKSelector {
    private static final Comparator<ScoredDocument> BEST_FIRST = Comparator
            .comparingDouble(ScoredDocument::score).reversed()
            .thenComparing(ScoredDocument::documentId);

    // PriorityQueue exposes the comparator's smallest element. Reversing the complete
    // final order puts the worst score, and then the largest tied ID, at its head.
    // Reversing only the score would incorrectly evict the smallest ID on a tie.
    private static final Comparator<ScoredDocument> WORST_FIRST = BEST_FIRST.reversed();

    private TopKSelector() {
    }

    /**
     * Visits candidates once, retains at most min(k, N), then sorts only the retained
     * results. K is any positive integer here; HTTP limits belong to the later API.
     * A small initial heap capacity avoids allocating k slots when k is enormous and
     * there are few candidates. No input mutation, threshold or implicit truncation
     * is applied. Duplicate IDs are excluded by the caller, avoiding an O(N) seen set.
     *
     * @return immutable results ordered by score descending, ASCII document ID ascending
     */
    public static List<ScoredDocument> select(Iterable<ScoredDocument> candidates, int k) {
        if (candidates == null || k < 1) {
            throw new IllegalArgumentException("Candidates must not be null and k must be positive");
        }
        PriorityQueue<ScoredDocument> heap = new PriorityQueue<>(Math.min(k, 64), WORST_FIRST);
        for (ScoredDocument candidate : candidates) {
            if (candidate == null) {
                throw new IllegalArgumentException("Candidate must not be null");
            }
            if (heap.size() < k) {
                heap.add(candidate);
            } else if (BEST_FIRST.compare(candidate, heap.peek()) < 0) {
                heap.poll();
                heap.add(candidate);
            }
        }
        List<ScoredDocument> result = new ArrayList<>(heap);
        result.sort(BEST_FIRST);
        return List.copyOf(result);
    }
}
