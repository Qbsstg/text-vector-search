package dev.twelvetimes.vector.search;

import java.util.regex.Pattern;

/** Immutable score for an already eligible document; neither persistence nor HTTP is involved. */
public record ScoredDocument(String documentId, double score) {
    private static final Pattern DOCUMENT_ID = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");

    /**
     * Enforces the frozen ASCII ID and cosine range at the score boundary. Canonical
     * positive zero is necessary because Double.compare distinguishes signed zeros,
     * whereas the ranking contract deliberately treats them as the same score.
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
