package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.VectorSearchApplication;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.search.CosineSimilarity;
import dev.twelvetimes.vector.search.ScoredDocument;
import dev.twelvetimes.vector.search.TopKSelector;
import java.io.File;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.SpringApplication;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeout;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 独立契约验收；固定黄金摘要来自保留在仓库中的Python参考脚本。 */
class B1AlgorithmIT {
    @TempDir Path directory;

    record Golden(int rounds, String text, String sha256) {
    }

    static Stream<Golden> goldens() {
        return Stream.of(
                new Golden(32, "", "5f70bf18a086007016e948b04aed3b82103a36bea41755b6cddfaf10ace3c6ef"),
                new Golden(1, "ababa", "8143223f939f094fb650e8cb55c9fffd6d88b68d5ac1e1513a3462acb3e24bf9"),
                new Golden(32, "ababa", "ad82c864c2dec3476778eeaf5f6e83a64d5827b9f029f22737e7e33523125ad1"),
                new Golden(32, "\u3000ＡＢＣ\u00a0DEF\u2007\tＧ\u3000", "f68a6f7015d536d6ddfb5bf7b0b7fd9036ed521577cf119e8b2928481d6bed27"),
                new Golden(32, "中文，单字。", "997322eaeec761fe4baf97f99bf2fafd645d2c25e086054eec175a4e903ba1a9"),
                new Golden(32, "😀🚀 😀", "7bfc3db8f9a5aced71a1c19b24eff89e623d7c2de3f9e78d12f020831d7b2c81"),
                new Golden(32, "e\u0301 É", "bb5cf321ecaae9370152d6ab7cb0218b2a1b83dd2a0b5bfc212dc906ac39941c"),
                new Golden(32, "İ I", "96daf7756554e19ba7787d39c1498a3ba941701f47a661a26feaf7ff405c9b34"));
    }

    @ParameterizedTest
    @MethodSource("goldens")
    void matchesIndependentlyGeneratedGoldenBytes(Golden golden) throws Exception {
        var embedder = new CharNgramTextEmbedder(golden.rounds(), 20000);
        float[] vector = embedder.embed(golden.text());
        assertEquals(256, vector.length);
        assertEquals("char12-sha256-r" + golden.rounds() + "-nfkc-root-ws-v1", embedder.version());
        byte[] raw = rawBytes(vector);
        assertEquals(golden.sha256(), sha256(raw));
        assertArrayEquals(raw, VectorCodec.encode(vector));
        assertArrayEquals(raw, rawBytes(embedder.embed(golden.text())));
    }

    @Test
    void separateJvmReportsSameVersionDigestAndEveryEncodedByte() throws Exception {
        String javaExecutable = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        // 只加载探针与无框架依赖的生产算法这两个类目录，避免借助Spring启动验证。
        String classpath = Path.of(B1VectorProbe.class.getProtectionDomain().getCodeSource().getLocation().toURI())
                + File.pathSeparator
                + Path.of(TextEmbedder.class.getProtectionDomain().getCodeSource().getLocation().toURI());
        for (Golden golden : goldens().toList()) {
            Process process = new ProcessBuilder(javaExecutable, "-cp", classpath, B1VectorProbe.class.getName(),
                    Integer.toString(golden.rounds()), Base64.getEncoder().encodeToString(
                            golden.text().getBytes(StandardCharsets.UTF_8))).redirectErrorStream(true).start();
            try {
                assertTrue(process.waitFor(15, TimeUnit.SECONDS), "Probe timeout");
                String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
                assertEquals(0, process.exitValue(), output);
                List<String> lines = output.lines().toList();
                assertEquals(3, lines.size(), output);
                assertEquals("char12-sha256-r" + golden.rounds() + "-nfkc-root-ws-v1", lines.get(0));
                assertEquals(golden.sha256(), lines.get(1));
                byte[] child = Base64.getDecoder().decode(lines.get(2));
                assertEquals(1024, child.length);
                assertEquals(golden.sha256(), sha256(child));
                assertArrayEquals(rawBytes(new CharNgramTextEmbedder(golden.rounds(), 20000).embed(golden.text())), child);
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    assertTrue(process.waitFor(5, TimeUnit.SECONDS), "Probe must terminate");
                }
                process.getInputStream().close();
            }
        }
    }

    @Test
    void sharedInstanceIsDeterministicUnderControlledConcurrencyAndReturnsFreshArrays() throws Exception {
        var embedder = new CharNgramTextEmbedder(32, 20000);
        List<Golden> samples = goldens().filter(g -> g.rounds() == 32).toList();
        var barrier = new CyclicBarrier(samples.size());
        var executor = Executors.newFixedThreadPool(samples.size());
        try {
            List<Callable<Void>> work = new ArrayList<>();
            for (Golden golden : samples) {
                work.add(() -> {
                    barrier.await(5, TimeUnit.SECONDS);
                    for (int repetition = 0; repetition < 15; repetition++) {
                        float[] first = embedder.embed(golden.text());
                        assertEquals(golden.sha256(), sha256(rawBytes(first)));
                        Arrays.fill(first, 42.0f);
                        float[] second = embedder.embed(golden.text());
                        assertNotSame(first, second);
                        assertEquals(golden.sha256(), sha256(rawBytes(second)));
                    }
                    return null;
                });
            }
            for (var future : executor.invokeAll(work, 20, TimeUnit.SECONDS)) {
                future.get(1, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void unicodeBoundariesExpansionAndDefaultLocaleFollowContract() throws Exception {
        var embedder = new CharNgramTextEmbedder(1, 20000);
        assertArrayEquals(rawBytes(embedder.embed("a b")), rawBytes(embedder.embed("a\u202fb")));
        assertArrayEquals(new byte[1024], rawBytes(embedder.embed("\u00a0\u2007\u202f\t\n\u3000")));
        assertArrayEquals(rawBytes(embedder.embed("a")), rawBytes(embedder.embed("Ａ")));
        assertThrows(IllegalArgumentException.class, () -> embedder.embed(null));
        for (String invalid : List.of("\ud800", "\udc00", "a\ud800b", "😀\udc00")) {
            assertThrows(IllegalArgumentException.class, () -> embedder.embed(invalid));
        }
        var oneCodePoint = new CharNgramTextEmbedder(1, 1);
        assertEquals(256, oneCodePoint.embed("😀").length);
        assertThrows(IllegalArgumentException.class, () -> oneCodePoint.embed("😀😀"));
        // U+FB03原文仅占一个码点，但NFKC会将其扩张为三个ASCII字母。
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(1, 2).embed("ﬃ"));
        assertThrows(IllegalArgumentException.class, () -> oneCodePoint.embed("İ"));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(1, 2).embed("   "));
        assertEquals(256, new CharNgramTextEmbedder(1, 3).embed("ﬃ").length);
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            assertEquals("96daf7756554e19ba7787d39c1498a3ba941701f47a661a26feaf7ff405c9b34",
                    sha256(rawBytes(new CharNgramTextEmbedder(32, 20000).embed("İ I"))));
        } finally {
            Locale.setDefault(previous);
        }
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(0, 10));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(10001, 10));
        assertThrows(IllegalArgumentException.class, () -> new CharNgramTextEmbedder(1, 0));
    }

    @Test
    void codecPreservesRawFiniteBitsAndBigEndianWithIndependentReference() {
        float[] values = new float[256];
        float[] edge = {0.0f, -0.0f, Float.MIN_VALUE, -Float.MIN_VALUE,
                Float.MIN_NORMAL, -Float.MIN_NORMAL, Float.MAX_VALUE, -Float.MAX_VALUE, 1.0f, -1.0f};
        for (int index = 0; index < values.length; index++) {
            values[index] = edge[index % edge.length];
        }
        byte[] encoded = VectorCodec.encode(values);
        assertEquals(1024, encoded.length);
        assertArrayEquals(rawBytes(values), encoded);
        assertEquals(0x80, Byte.toUnsignedInt(encoded[4]));
        float[] decoded = VectorCodec.decode(encoded);
        assertNotSame(values, decoded);
        assertArrayEquals(rawBytes(values), rawBytes(decoded));
        encoded[0] = 0x3f;
        assertEquals(0, Float.floatToRawIntBits(decoded[0]), "Decode must not retain mutable byte storage");
        decoded[0] = 9.0f;
        assertEquals(0, Float.floatToRawIntBits(values[0]));
        assertNotSame(VectorCodec.encode(values), VectorCodec.encode(values));
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(null));
        assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(null));
        for (int length : new int[]{0, 255, 257}) {
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(new float[length]));
        }
        for (int length : new int[]{0, 1023, 1025}) {
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(new byte[length]));
        }
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            float[] invalid = new float[256];
            invalid[255] = bad;
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.encode(invalid));
            byte[] invalidBytes = rawBytes(invalid);
            assertThrows(IllegalArgumentException.class, () -> VectorCodec.decode(invalidBytes));
        }
    }

    @Test
    void cosineHandlesNonUnitExtremeZeroOppositeAndInvalidVectors() {
        float[] a = axis(2.0f, 0.0f);
        float[] b = axis(3.0f, 4.0f);
        assertEquals(0.6, CosineSimilarity.score(a, b), 1e-15);
        assertEquals(-1.0, CosineSimilarity.score(a, axis(-7.0f, 0.0f)), 1e-15);
        assertEquals(1.0, CosineSimilarity.score(axis(Float.MAX_VALUE, Float.MAX_VALUE),
                axis(Float.MAX_VALUE, Float.MAX_VALUE)), 1e-15);
        assertEquals(1.0, CosineSimilarity.score(axis(Float.MIN_VALUE, 0.0f),
                axis(Float.MIN_VALUE, 0.0f)), 1e-15);
        assertEquals(0L, Double.doubleToRawLongBits(CosineSimilarity.score(new float[256], a)));
        assertEquals(0L, Double.doubleToRawLongBits(CosineSimilarity.score(axis(-0.0f, 0.0f), a)));
        assertEquals(0L, Double.doubleToRawLongBits(CosineSimilarity.score(a, axis(-0.0f, -2.0f))));
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(null, a));
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(a, new float[255]));
        assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(new float[257], a));
        for (float bad : new float[]{Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY}) {
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(axis(bad, 1), a));
            assertThrows(IllegalArgumentException.class, () -> CosineSimilarity.score(a, axis(1, bad)));
        }
    }

    @Test
    void topKMatchesIndependentFullSortAcrossShufflesTiesAndKBoundaries() {
        Random random = new Random(20261022L);
        List<ScoredDocument> candidates = new ArrayList<>();
        // 将分数离散化以构造跨堆边界的大量同分项，同时覆盖负分。
        for (int index = 0; index < 137; index++) {
            candidates.add(new ScoredDocument(String.format(Locale.ROOT, "doc-%03d", index),
                    (random.nextInt(21) - 10) / 10.0));
        }
        List<ScoredDocument> expected = new ArrayList<>(candidates);
        // 参考答案完整排序所有候选，不复用生产堆或生产比较器。
        expected.sort(Comparator.comparingDouble(ScoredDocument::score).reversed()
                .thenComparing(ScoredDocument::documentId));
        for (int attempt = 0; attempt < 15; attempt++) {
            Collections.shuffle(candidates, random);
            List<ScoredDocument> snapshot = List.copyOf(candidates);
            for (int k : new int[]{1, 2, 7, 100, 137, 200}) {
                List<ScoredDocument> actual = TopKSelector.select(candidates, k);
                assertEquals(expected.subList(0, Math.min(k, expected.size())), actual);
                assertThrows(UnsupportedOperationException.class,
                        () -> actual.add(new ScoredDocument("extra", 0.0)));
            }
            assertEquals(snapshot, candidates);
        }
    }

    @Test
    void topKRetainsNegativeScoresAndNormalizesSignedZeroAtTieBoundary() {
        List<ScoredDocument> ties = List.of(new ScoredDocument("z", 0.0), new ScoredDocument("b", -0.0),
                new ScoredDocument("a", 0.0), new ScoredDocument("c", -0.5), new ScoredDocument("d", -1.0));
        assertEquals(List.of("a", "b"), TopKSelector.select(ties, 2).stream().map(ScoredDocument::documentId).toList());
        assertEquals(List.of("a", "b", "z", "c", "d"),
                TopKSelector.select(ties, 1000).stream().map(ScoredDocument::documentId).toList());
        assertEquals(0L, Double.doubleToRawLongBits(new ScoredDocument("zero", -0.0).score()));
        assertTrue(TopKSelector.select(List.of(), 1).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(ties, 0));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(ties, -1));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(null, 1));
        assertThrows(IllegalArgumentException.class, () -> TopKSelector.select(Arrays.asList((ScoredDocument) null), 1));
        for (double score : new double[]{Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, -1.01, 1.01}) {
            assertThrows(IllegalArgumentException.class, () -> new ScoredDocument("invalid-score", score));
        }
        for (String id : new String[]{null, "", "bad/id", "中文"}) {
            assertThrows(IllegalArgumentException.class, () -> new ScoredDocument(id, 0.0));
        }
    }

    @Test
    void productionSpringWiringUsesSingleEmbedderAndConfiguredRounds() {
        assertTimeout(Duration.ofSeconds(20), () -> {
            try (var context = new SpringApplication(VectorSearchApplication.class).run(
                    "--spring.main.web-application-type=none", "--spring.main.banner-mode=off",
                    "--logging.level.root=WARN", "--spring.datasource.url=jdbc:h2:file:"
                            + directory.resolve("wiring/knowledge").toAbsolutePath() + ";WRITE_DELAY=0",
                    "--spring.datasource.username=sa", "--spring.datasource.password=",
                    "--vector.cpu-rounds=1", "--vector.max-text-code-points=8")) {
                assertEquals(1, context.getBeansOfType(TextEmbedder.class).size());
                assertEquals(1, context.getBean(VectorProperties.class).cpuRounds());
                TextEmbedder embedder = context.getBean(TextEmbedder.class);
                assertEquals("char12-sha256-r1-nfkc-root-ws-v1", embedder.version());
                assertEquals("8143223f939f094fb650e8cb55c9fffd6d88b68d5ac1e1513a3462acb3e24bf9",
                        sha256(rawBytes(embedder.embed("ababa"))));
                assertThrows(IllegalArgumentException.class, () -> embedder.embed("123456789"));
            }
        });
    }

    /** 直接读取原始位，避免黄金验收与可能有误的生产codec互相掩盖问题。 */
    private static byte[] rawBytes(float[] vector) {
        ByteBuffer buffer = ByteBuffer.allocate(vector.length * Float.BYTES).order(ByteOrder.BIG_ENDIAN);
        for (float value : vector) {
            buffer.putInt(Float.floatToRawIntBits(value));
        }
        return buffer.array();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static float[] axis(float first, float second) {
        float[] vector = new float[256];
        vector[0] = first;
        vector[1] = second;
        return vector;
    }
}
