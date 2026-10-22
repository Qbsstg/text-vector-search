package dev.twelvetimes.vector.search;

import java.util.regex.Pattern;

/** 保存已通过资格过滤的文档及分数，不涉及持久化或 HTTP；实例不可变。 */
public record ScoredDocument(String documentId, double score) {
    private static final Pattern DOCUMENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    /**
     * 在分数边界执行冻结的 ASCII ID 和余弦范围约束。
     * Double.compare 会区分正负零，而排序契约要求它们同分，因此统一为正零。
     */
    public ScoredDocument {
        if (documentId == null || !DOCUMENT_ID.matcher(documentId).matches()) {
            throw new IllegalArgumentException("Invalid document ID");
        }
        if (!Double.isFinite(score) || score < -1.0 || score > 1.0) {
            throw new IllegalArgumentException("Score must be finite and between -1 and 1");
        }
        score = score == 0.0 ? 0.0 : score;
    }
}
