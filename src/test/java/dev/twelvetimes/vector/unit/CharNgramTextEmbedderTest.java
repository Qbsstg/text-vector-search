package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CharNgramTextEmbedderTest {
    private final TextEmbedder embedder = new CharNgramTextEmbedder(32, 20000);

    @Test
    void versionIncludesRoundsAndConfigurationBoundsAreEnforced() {
        assertEquals("char12-sha256-r32-nfkc-root-ws-v1", embedder.version());
        assertEquals("char12-sha256-r1-nfkc-root-ws-v1", new CharNgramTextEmbedder(1, 1).version());
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(0, 1));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(10001, 1));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(1, 0));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(1, 20001));
    }

    @Test
    void emptyAndUnicodeWhitespaceProduceFreshPositiveZeroVectors() {
        float[] empty = embedder.embed("");
        assertEquals(256, empty.length);
        assertArrayEquals(new int[256], bits(empty));
        assertArrayEquals(bits(empty), bits(embedder.embed(" \t\n\u00a0\u2007\u202f\u3000")));
        assertNotSame(empty, embedder.embed(""));
        empty[0] = 1;
        assertArrayEquals(new int[256], bits(embedder.embed("")));
    }

    @Test
    void nfkcRootCaseAndWhitespaceRulesMatchCanonicalText() {
        assertArrayEquals(bits(embedder.embed("ab 你🙂")), bits(embedder.embed("\u3000ＡＢ\u00a0\t你🙂\n")));
        assertArrayEquals(bits(embedder.embed("i")), bits(embedder.embed("I")));
        assertArrayEquals(bits(embedder.embed("ffi")), bits(embedder.embed("\ufb03")));
        assertFalse(Arrays.equals(bits(embedder.embed("a b")), bits(embedder.embed("ab"))),
                "Bigrams must not cross a whitespace boundary");
        assertFalse(Arrays.equals(bits(embedder.embed("ab")), bits(embedder.embed("a!b"))),
                "Punctuation remains a feature, not a removed separator");
    }

    @Test
    void singleChineseAndSupplementaryCodePointsProduceFiniteUnitVectors() {
        for (String text : new String[] {"字", "🙂", "中文测试", "重复字符AAAA"}) {
            float[] vector = embedder.embed(text);
            double squaredNorm = 0;
            for (float value : vector) {
                assertTrue(Float.isFinite(value));
                squaredNorm += (double) value * value;
            }
            assertEquals(1.0, squaredNorm, 0.000001);
        }
        assertArrayEquals(bits(embedder.embed("字")), bits(embedder.embed("字 字")),
                "Repeated unigrams preserve frequency before normalization and no cross-space bigrams");
    }

    @Test
    void rawCodePointLimitRejectsLongInputButDoesNotCountUtf16Units() {
        TextEmbedder limited = new CharNgramTextEmbedder(1, 2);
        assertEquals(256, limited.embed("🙂字").length);
        assertThrows(IllegalArgumentException.class, () -> limited.embed("🙂字a"));
        assertThrows(IllegalArgumentException.class, () -> limited.embed("   "));
    }

    @Test
    void normalizedExpansionHasItsOwnLimit() {
        TextEmbedder limited = new CharNgramTextEmbedder(1, 1);
        assertThrows(IllegalArgumentException.class, () -> limited.embed("\ufb03"));
        assertThrows(IllegalArgumentException.class, () -> limited.embed("\u0130"));
        assertEquals(256, new CharNgramTextEmbedder(1, 3).embed("\ufb03").length);
    }

    @Test
    void nullAndMalformedUtf16AreRejectedBeforeUtf8Replacement() {
        assertThrows(IllegalArgumentException.class, () -> embedder.embed(null));
        for (String text : new String[] {"\ud800", "\udc00", "a\ud800b", "\ud800\ud800", "🙂\udc00"}) {
            assertThrows(IllegalArgumentException.class, () -> embedder.embed(text));
        }
    }

    @Test
    void fixedGoldenByteDigestsMatchIndependentReference() throws Exception {
        // 固定期望由独立 Python hashlib/struct 参考实现生成，
        // 用于发现位映射、轮数、特征前缀、规范化及字节序的意外变化。
        assertEquals("5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef", hash(embedder.embed("")));
        assertEquals("d2987869c01cdc5afa71fd78034ccee55022ab11b288d7c3da9eccadcc0aa207",
                hash(new CharNgramTextEmbedder(1, 20000).embed("a")));
        assertEquals("e5d2ef07b4dbefb919a4eb325c8d3d8e216dd3584d86613a8d2a816b4a8aaf85", hash(embedder.embed("Ａb 你🙂")));
        assertEquals("f56126b4d52753bf7286c72a0c9f486b121f2197e2d08049e663daf15f761ca7", hash(embedder.embed("重复字符AAAA")));
    }

    @Test
    void oneInstanceIsBitIdenticalAcrossRepeatedAndConcurrentCalls() throws Exception {
        String text = "确定性🙂 ＡＢＣ  repeated text";
        int[] expected = bits(embedder.embed(text));
        for (int index = 0; index < 10; index++) {
            assertArrayEquals(expected, bits(embedder.embed(text)));
        }
        var executor = Executors.newFixedThreadPool(4);
        try {
            var tasks = new ArrayList<Callable<int[]>>();
            for (int index = 0; index < 24; index++) {
                tasks.add(() -> bits(embedder.embed(text)));
            }
            for (var result : executor.invokeAll(tasks)) {
                assertArrayEquals(expected, result.get());
            }
        } finally {
            executor.shutdownNow();
        }
    }

    private static int[] bits(float[] vector) {
        int[] bits = new int[vector.length];
        for (int index = 0; index < vector.length; index++) {
            bits[index] = Float.floatToRawIntBits(vector[index]);
        }
        return bits;
    }

    private static String hash(float[] vector) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(VectorCodec.encode(vector)));
    }
}
