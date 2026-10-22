package dev.twelvetimes.vector.embedding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.util.Locale;

/**
 * 按 Java 17 的 B1 契约生成确定性的字符 unigram/bigram 哈希向量。
 * 算法保留局部字面重合，不具备语义理解能力。所有可变计算状态均属于单次调用，
 * 因此同一实例可以安全地供多个线程复用。
 */
public final class CharNgramTextEmbedder implements TextEmbedder {
    private final int cpuRounds;
    private final int maxTextCodePoints;
    private final String version;

    /**
     * @param cpuRounds 每个特征的 SHA-256 总轮数，包含首次 UTF-8 字节摘要
     * @param maxTextCodePoints 分别约束原文和完整规范化文本的 code point 上限
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
                previous = -1; // 折叠后的空白是特征边界，不能跨越它生成 bigram。
                continue;
            }
            String character = new String(Character.toChars(current));
            accumulate("U:" + character, buckets, digest);
            if (previous >= 0) {
                accumulate("B:" + new String(Character.toChars(previous)) + character, buckets, digest);
            }
            previous = current;
        }

        // 先转为 double 再相乘，防止整数溢出改变范数。
        // 固定下标顺序并使用 StrictMath，避免并行归约或平台平方根实现影响确定性。
        double squaredNorm = 0.0;
        for (long bucket : buckets) {
            squaredNorm += (double) bucket * bucket;
        }
        float[] result = new float[DIMENSIONS];
        if (squaredNorm == 0.0) {
            return result; // 空输入和带符号碰撞完全抵消均得到合法零向量。
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
            // isWhitespace 不包含 NBSP，需结合 isSpaceChar 才满足冻结的空白判定。
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
        return result.toString(); // 不追加末尾待定空格，保证契约要求的首尾去空白。
    }

    private void accumulate(String feature, long[] buckets, MessageDigest digest) {
        byte[] bytes = feature.getBytes(StandardCharsets.UTF_8);
        for (int round = 0; round < cpuRounds; round++) {
            // digest(byte[]) 每轮结束会重置本次调用独有的 MessageDigest。
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
            // Java 17 必须支持 SHA-256；缺失表示运行环境故障，不能归为用户输入错误。
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
