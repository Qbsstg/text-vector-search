package dev.twelvetimes.vector.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Deterministic character unigram/bigram hashing under the Java 17 B1 contract.
 * This preserves local literal overlap, not semantic understanding. All mutable
 * calculation state belongs to one invocation, so the same instance is thread safe.
 */
public final class CharNgramTextEmbedder implements TextEmbedder {
    private final int cpuRounds;
    private final int maxTextCodePoints;
    private final String version;

    /**
     * @param cpuRounds total SHA-256 rounds per feature, including the first UTF-8 digest
     * @param maxTextCodePoints independent upper bound for raw and fully normalized text
     */
    public CharNgramTextEmbedder(int cpuRounds, int maxTextCodePoints) {
        if (cpuRounds < 1 || cpuRounds > 10000) {
            throw new IllegalArgumentException("cpuRounds must be between 1 and 10000");
        }
        if (maxTextCodePoints < 1 || maxTextCodePoints > 20000) {
            throw new IllegalArgumentException("maxTextCodePoints must be between 1 and 20000");
        }
        this.cpuRounds = cpuRounds;
        this.maxTextCodePoints = maxTextCodePoints;
        this.version = "char12-sha256-r" + cpuRounds + "-nfkc-root-ws-v1";
    }

    @Override
    public String version() {
        return version;
    }

    @Override
    public float[] embed(String text) {
        validateRawText(text);
        String normalized = normalize(text);
        if (normalized.codePointCount(0, normalized.length()) > maxTextCodePoints) {
            throw new IllegalArgumentException("Normalized text exceeds code point limit");
        }

        long[] buckets = new long[DIMENSIONS];
        MessageDigest digest = newDigest();
        int previous = -1;
        for (int offset = 0; offset < normalized.length();) {
            int current = normalized.codePointAt(offset);
            offset += Character.charCount(current);
            if (current == ' ') {
                previous = -1; // A collapsed whitespace boundary must never produce a bigram.
                continue;
            }
            String character = new String(Character.toChars(current));
            accumulate("U:" + character, buckets, digest);
            if (previous >= 0) {
                accumulate("B:" + new String(Character.toChars(previous)) + character, buckets, digest);
            }
            previous = current;
        }

        // Cast before multiplying: integer overflow must not change a future larger input's norm.
        // Fixed index order and StrictMath avoid parallel reduction/platform sqrt differences.
        double squaredNorm = 0.0;
        for (long bucket : buckets) {
            squaredNorm += (double) bucket * bucket;
        }
        float[] result = new float[DIMENSIONS];
        if (squaredNorm == 0.0) {
            return result; // Empty input and complete signed collisions are both valid zero vectors.
        }
        double norm = StrictMath.sqrt(squaredNorm);
        for (int index = 0; index < DIMENSIONS; index++) {
            result[index] = (float) (buckets[index] / norm);
        }
        return result;
    }

    private void validateRawText(String text) {
        if (text == null) {
            throw new IllegalArgumentException("Text must not be null");
        }
        int codePoints = 0;
        for (int offset = 0; offset < text.length(); offset++) {
            char character = text.charAt(offset);
            if (Character.isHighSurrogate(character)) {
                if (offset + 1 >= text.length() || !Character.isLowSurrogate(text.charAt(offset + 1))) {
                    throw new IllegalArgumentException("Text contains an unpaired high surrogate");
                }
                offset++;
            } else if (Character.isLowSurrogate(character)) {
                throw new IllegalArgumentException("Text contains an unpaired low surrogate");
            }
            if (++codePoints > maxTextCodePoints) {
                throw new IllegalArgumentException("Raw text exceeds code point limit");
            }
        }
    }

    private String normalize(String text) {
        String folded = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(folded.length());
        boolean pendingSpace = false;
        for (int offset = 0; offset < folded.length();) {
            int codePoint = folded.codePointAt(offset);
            offset += Character.charCount(codePoint);
            // isWhitespace alone excludes NBSP; isSpaceChar completes the frozen predicate.
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)) {
                pendingSpace = !result.isEmpty();
            } else {
                if (pendingSpace) {
                    result.append(' ');
                }
                result.appendCodePoint(codePoint);
                pendingSpace = false;
            }
        }
        return result.toString(); // Never flush a final pending space: trimming is part of the contract.
    }

    private void accumulate(String feature, long[] buckets, MessageDigest digest) {
        byte[] bytes = feature.getBytes(StandardCharsets.UTF_8);
        for (int round = 0; round < cpuRounds; round++) {
            // digest(byte[]) resets the invocation-local MessageDigest after every round.
            bytes = digest.digest(bytes);
        }
        int bucket = Byte.toUnsignedInt(bytes[0]);
        int sign = (bytes[1] & 1) == 0 ? 1 : -1;
        buckets[bucket] += sign;
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            // SHA-256 is mandatory in Java 17; this is a runtime installation failure, not bad input.
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
