package dev.twelvetimes.vector.search;

/** 候选只包含本次快照必需字段与有界前缀；防御复制阻止外部修改已读取的向量字节。 */
public record SearchCandidate(String documentId, String channel, byte[] vectorBytes, String prefix) {
    public SearchCandidate {
        if (vectorBytes == null) { throw new IllegalArgumentException("候选向量不能为空"); }
        vectorBytes = vectorBytes.clone();
    }

    @Override public byte[] vectorBytes() { return vectorBytes.clone(); }
}
