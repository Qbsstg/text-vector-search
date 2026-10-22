package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.embedding.VectorCodec;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VectorCodecTest {
    @Test
    void finiteRawBitsRoundTripIncludingNegativeZeroAndExtremes() {
        float[] vector = new float[256];
        float[] cases = {0.0f, -0.0f, 1.0f, -1.0f, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.MIN_NORMAL, -Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE};
        for (int index = 0; index < vector.length; index++) {
            vector[index] = cases[index % cases.length];
        }
        byte[] bytes = VectorCodec.encode(vector);
        float[] decoded = VectorCodec.decode(bytes);
        assertEquals(1024, bytes.length);
        assertNotSame(vector, decoded);
        for (int index = 0; index < vector.length; index++) {
            assertEquals(Float.floatToRawIntBits(vector[index]), Float.floatToRawIntBits(decoded[index]));
        }
        assertArrayEquals(new byte[] {0, 0, 0, 0, (byte) 0x80, 0, 0, 0, 0x3f, (byte) 0x80, 0, 0},
                Arrays.copyOf(bytes, 12));
    }

    @Test
    void encodeAndDecodeDoNotRetainInputStorage() {
        float[] vector = new float[256];
        vector[0] = 1;
        byte[] bytes = VectorCodec.encode(vector);
        vector[0] = 2;
        assertEquals(1.0f, VectorCodec.decode(bytes)[0]);
        float[] decoded = VectorCodec.decode(bytes);
        bytes[0] = 0;
        assertEquals(1.0f, decoded[0]);
    }

    @Test
    void nullAndWrongSizesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(null));
        for (int size : new int[] {0, 255, 257}) {
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(new float[size]));
        }
        for (int size : new int[] {0, 1023, 1025}) {
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(new byte[size]));
        }
    }

    @Test
    void nonFiniteValuesAreRejectedAtBothBoundaries() {
        for (float value : new float[] {Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] vector = new float[256];
            vector[255] = value;
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(vector));
            byte[] bytes = new byte[1024];
            ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN).putFloat(1020, value);
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(bytes));
        }
    }
}
