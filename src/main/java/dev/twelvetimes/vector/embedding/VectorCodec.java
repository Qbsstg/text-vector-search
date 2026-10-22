package dev.twelvetimes.vector.embedding;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * 将 256 个 float 稳定编码为二进制，不依赖本机字节序或 Java 对象序列化。
 * 编解码均显式采用 BIG_ENDIAN；有限值的原始位（含负零）往返不变。
 * 拒绝 NaN 和无穷大，避免非有限数污染后续相似度计算。
 */
public final class VectorCodec {
    public static final int ENCODED_LENGTH = TextEmbedder.DIMENSIONS * Float.BYTES;

    private VectorCodec() {
    }

    /** 校验完整输入，返回新分配的 1024 字节存储。 */
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

    /** 校验字节长度及每个解码元素，不保留对输入字节数组的引用。 */
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
