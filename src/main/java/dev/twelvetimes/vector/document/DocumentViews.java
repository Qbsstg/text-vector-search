package dev.twelvetimes.vector.document;

import dev.twelvetimes.vector.api.contract.ApiModels.DocumentDetail;
import dev.twelvetimes.vector.api.contract.ApiModels.DocumentSummary;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.persistence.DocumentRow;

/** 显式组装公开快照，不泄露向量和执行令牌；失效不影响向量就绪判定。 */
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
}
