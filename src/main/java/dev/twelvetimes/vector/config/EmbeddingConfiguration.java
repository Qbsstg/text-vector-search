package dev.twelvetimes.vector.config;

import dev.twelvetimes.vector.embedding.CharNgramTextEmbedder;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 统一绑定不可变算法实例，防止文档与查询路径悄悄采用不同配置。 */
@Configuration(proxyBeanMethods = false)
public class EmbeddingConfiguration {
    @Bean
    public TextEmbedder textEmbedder(VectorProperties properties) {
        return new CharNgramTextEmbedder(properties.cpuRounds(), properties.maxTextCodePoints());
    }
}
