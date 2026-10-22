package dev.twelvetimes.vector.embedding;

/**
 * Narrow, framework-independent boundary shared by document and query vectorization.
 * Implementations return independent arrays and expose the exact algorithm version;
 * callers must never compare persisted vectors from a different version.
 */
public interface TextEmbedder {
    int DIMENSIONS = 256;

    /** Returns a fresh finite vector; null, malformed UTF-16 or oversized input is rejected. */
    float[] embed(String text);

    /** Includes every configurable parameter that can change vector bits. */
    String version();
}
