package dev.twelvetimes.vector.embedding;

/**
 * 文档和查询共用的窄向量化接口，不依赖框架。
 * 实现应返回独立数组并提供精确算法版本；调用者不能比较不同版本的持久化向量。
 */
public interface TextEmbedder {
    int DIMENSIONS = 256;

    /** 返回新分配的有限值向量；拒绝 null、非法 UTF-16 和超限文本。 */
    float[] embed(String text);

    /** 版本标识包含所有可能改变向量位模式的可配置参数。 */
    String version();
}
