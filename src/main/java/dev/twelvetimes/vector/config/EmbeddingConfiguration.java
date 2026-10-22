package dev.twelvetimes.vector.config;

import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Binds one immutable algorithm instance so document and query paths cannot silently diverge. */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {
    @Bean
    public TextEmbedder textEmbedder(VectorProperties properties) {
        return new CharNgramTextEmbedder(properties.cpuRounds(), properties.maxTextCodePoints());
    }
}
