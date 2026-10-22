package dev.twelvetimes.vector.search;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;

/** 用有界堆精确选择结果；调用者须提前完成资格过滤并保证 ID 唯一。 */
public final class TopKSelector {
    private static final Comparator<ScoredDocument> BEST_FIRST = Comparator
            .comparingDouble(ScoredDocument::score).reversed()
            .thenComparing(ScoredDocument::documentId);

    // PriorityQueue 的堆头为比较器中的最小元素。反转完整最终顺序，
    // 才能让最低分及同分时最大 ID 位于堆头；只反转分数会错误淘汰较小 ID。
    private static final Comparator<ScoredDocument> WORST_FIRST = BEST_FIRST.reversed();

    private TopKSelector() {
    }

    /**
     * 单次遍历候选，最多保留 min(k, N) 项，最后仅排序保留结果。
     * 此处允许任意正整数 K，HTTP 上限由后续 API 层负责。
     * 使用较小初始堆容量，避免 K 很大而候选很少时预分配过量空间。
     * 不修改输入、不设分数阈值、不暗中截断候选；由调用者排除重复 ID，避免 O(N) 去重集合。
     *
     * @return 不可变结果，按分数降序、同分 ASCII 文档 ID 升序排列
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
