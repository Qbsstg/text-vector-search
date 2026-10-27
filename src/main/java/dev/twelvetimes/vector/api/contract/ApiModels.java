package dev.twelvetimes.vector.api.contract;

import java.time.Instant;
import java.util.List;

/** 六业务接口的传输格式；v1.1 仅为文档详情增加可空的持久向量只读信息。 */
public final class ApiModels {
    private ApiModels() { }

    public record SubmitDocumentRequest(String documentId, String text, String channel) { }
    public record SearchRequest(String text, Integer topK, String channel) { }
    public record Submission(String taskId, String documentId, TaskStatus status) { }
    public record TaskView(String taskId, String documentId, TaskStatus status,
                           Instant submittedAt, Instant startedAt, Instant finishedAt,
                           String errorCode, String errorMessage, DocumentDetail document) { }
    public record DocumentSummary(String documentId, String channel, String taskId, TaskStatus taskStatus,
                                  boolean vectorReady, boolean invalidated, Instant submittedAt,
                                  Instant vectorCompletedAt, Instant invalidatedAt, String hitCount) { }
    public record DocumentDetail(String documentId, String channel, String taskId, TaskStatus taskStatus,
                                 boolean vectorReady, boolean invalidated, Instant submittedAt,
                                 Instant vectorCompletedAt, Instant invalidatedAt, String hitCount, String text,
                                 VectorInfo vectorInfo) {
        /** 任务轮询保持原有轻量快照，不为内嵌文档额外读取完整向量。 */
        public DocumentDetail(String documentId, String channel, String taskId, TaskStatus taskStatus,
                              boolean vectorReady, boolean invalidated, Instant submittedAt,
                              Instant vectorCompletedAt, Instant invalidatedAt, String hitCount, String text) {
            this(documentId, channel, taskId, taskStatus, vectorReady, invalidated, submittedAt,
                    vectorCompletedAt, invalidatedAt, hitCount, text, null);
        }
    }
    /** 指纹来源于已持久化的原始字节；数值保留 float32 精度，不承载内部执行令牌。 */
    public record VectorInfo(String embeddingVersion, int dimensions, int byteLength, boolean zeroVector,
                             String sha256, List<Float> values) {
        public VectorInfo {
            // 对外快照独立持有不可变列表，避免外部修改构造时的集合影响后续响应。
            values = List.copyOf(values);
        }
    }
    public record DocumentPage(List<DocumentSummary> items, int page, int size, long totalElements) { }
    public record Invalidation(String documentId, boolean invalidated, Instant invalidatedAt) { }
    public record SearchHit(String documentId, String channel, double score, String snippet) { }
    public record SearchResponse(List<SearchHit> hits) { }
    public record FieldError(String field, String message) { }
    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) { }
}

