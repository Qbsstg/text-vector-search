package dev.twelvetimes.vector.document;

import dev.twelvetimes.vector.api.contract.ApiModels.DocumentDetail;
import dev.twelvetimes.vector.api.contract.ApiModels.DocumentSummary;
import dev.twelvetimes.vector.api.contract.ApiModels.VectorInfo;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.persistence.DocumentDetailRow;
import dev.twelvetimes.vector.persistence.DocumentRow;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/** 显式组装公开快照；仅完整详情附带向量信息，失效不影响向量就绪判定。 */
public final class DocumentViews {
    private DocumentViews() { }

    public static boolean ready(DocumentRow row) {
        return "SUCCEEDED".equals(row.status()) && Integer.valueOf(1024).equals(row.vectorLength());
    }

    public static DocumentSummary summary(DocumentRow row) {
        return new DocumentSummary(row.documentId(), row.channel(), row.taskId(), TaskStatus.valueOf(row.status()),
                ready(row), row.invalidated(), row.submittedAt(), row.vectorCompletedAt(), row.invalidatedAt(),
                Long.toString(row.hitCount()));
    }

    public static DocumentDetail detail(DocumentRow row) {
        return new DocumentDetail(row.documentId(), row.channel(), row.taskId(), TaskStatus.valueOf(row.status()),
                ready(row), row.invalidated(), row.submittedAt(), row.vectorCompletedAt(), row.invalidatedAt(),
                Long.toString(row.hitCount()), row.text());
    }

    /** 从持久化快照展示实际向量，不重新计算文本，也不使用当前算法版本覆盖历史版本。 */
    public static DocumentDetail detail(DocumentDetailRow row) {
        VectorInfo vectorInfo = vectorInfo(row);
        return new DocumentDetail(row.documentId(), row.channel(), row.taskId(), TaskStatus.valueOf(row.status()),
                vectorInfo != null, row.invalidated(), row.submittedAt(), row.vectorCompletedAt(), row.invalidatedAt(),
                Long.toString(row.hitCount()), row.text(), vectorInfo);
    }

    private static VectorInfo vectorInfo(DocumentDetailRow row) {
        if (!"SUCCEEDED".equals(row.status()) || row.vectorBytes() == null) { return null; }
        if (row.embeddingVersion() == null || row.embeddingVersion().isBlank() || row.vectorCompletedAt() == null) {
            throw new IllegalStateException("已完成向量缺少持久化版本或完成时间");
        }

        byte[] bytes = row.vectorBytes();
        final float[] vector;
        try {
            // 冻结 codec 负责长度、字节序及有限值校验；坏数据应产生服务端错误，不能伪造就绪信息。
            vector = VectorCodec.decode(bytes);
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("持久化向量无法解码", exception);
        }
        List<Float> values = new ArrayList<>(vector.length);
        boolean zeroVector = true;
        for (float value : vector) {
            values.add(value);
            // 数值比较使 +0 与 -0 均视为零；指纹仍直接使用原始字节，保留负零位的区别。
            if (value != 0.0f) { zeroVector = false; }
        }
        try {
            String sha256 = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
            return new VectorInfo(row.embeddingVersion(), vector.length, bytes.length, zeroVector,
                    sha256, List.copyOf(values));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("运行环境缺少 SHA-256", exception);
        }
    }
}
