package dev.twelvetimes.vector.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Stable 256-float binary representation, independent of native byte order and Java serialization.
 * BIG_ENDIAN is explicitly selected at both boundaries. Finite raw bits, including negative zero,
 * survive a round trip; NaN and infinity are rejected instead of poisoning later scores.
 */
public final class VectorCodec {
    public static final int ENCODED_LENGTH = TextEmbedder.DIMENSIONS * Float.BYTES;

    private VectorCodec() {
    }

    /** Validates the complete input and returns newly allocated 1024-byte storage. */
    public static byte[] encode(float[] vector) {
        if (vector == null || vector.length != TextEmbedder.DIMENSIONS) {
            throw new IllegalArgumentException("Vector must have exactly 256 dimensions");
        }
        ByteBuffer buffer = ByteBuffer.allocate(ENCODED_LENGTH).order(ByteOrder.BIG_ENDIAN);
        for (float value : vector) {
            requireFinite(value);
            buffer.putInt(Float.floatToRawIntBits(value));
        }
        return buffer.array();
    }

    /** Validates size and every decoded element; no reference to the source bytes is retained. */
    public static float[] decode(byte[] bytes) {
        if (bytes == null || bytes.length != ENCODED_LENGTH) {
            throw new IllegalArgumentException("Encoded vector must have exactly 1024 bytes");
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN);
        float[] vector = new float[TextEmbedder.DIMENSIONS];
        for (int index = 0; index < vector.length; index++) {
            float value = Float.intBitsToFloat(buffer.getInt());
            requireFinite(value);
            vector[index] = value;
        }
        return vector;
    }

    private static void requireFinite(float value) {
        if (!Float.isFinite(value)) {
            throw new IllegalArgumentException("Vector contains a non-finite value");
        }
    }
}
