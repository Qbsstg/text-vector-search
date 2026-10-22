package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

/** 独立JVM探针入口；标准输出固定为三行协议，不启动Spring应用。 */
public final class B1VectorProbe {
    private B1VectorProbe() {
    }

    public static void main(String[] args) throws Exception {
        int rounds = Integer.parseInt(args[0]);
        String text = new String(Base64.getDecoder().decode(args[1]), StandardCharsets.UTF_8);
        var embedder = new CharNgramTextEmbedder(rounds, 20000);
        byte[] encoded = VectorCodec.encode(embedder.embed(text));
        System.out.println(embedder.version());
        System.out.println(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(encoded)));
        System.out.println(Base64.getEncoder().encodeToString(encoded));
    }
}
