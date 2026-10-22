package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.config.EmbeddingConfiguration;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class EmbeddingConfigurationTest {
    @Test
    void springBindsOneEmbedderUsingTheExistingProperties() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.registerBean(VectorProperties.class, () -> new VectorProperties(1, 64, 1000,
                    5000, 1, 3, 262144, 100, 100, 2, 500, 32, 30));
            context.register(EmbeddingConfiguration.class);
            context.refresh();
            TextEmbedder embedder = context.getBean(TextEmbedder.class);
            assertEquals(1, context.getBeansOfType(TextEmbedder.class).size());
            assertSame(embedder, context.getBean(TextEmbedder.class));
            assertEquals("char12-sha256-r2-nfkc-root-ws-v1", embedder.version());
            assertThrows(IllegalArgumentException.class, () -> embedder.embed("four"));
        }
    }
}
