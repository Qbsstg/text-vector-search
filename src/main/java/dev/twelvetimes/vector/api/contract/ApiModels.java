package dev.twelvetimes.vector.api.contract;

import java.time.Instant;
import java.util.List;

/** Contract v1.0. These DTOs declare the agreed wire format; business endpoints follow C0. */
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
                                 Instant vectorCompletedAt, Instant invalidatedAt, String hitCount, String text) { }
    public record DocumentPage(List<DocumentSummary> items, int page, int size, long totalElements) { }
    public record Invalidation(String documentId, boolean invalidated, Instant invalidatedAt) { }
    public record SearchHit(String documentId, String channel, double score, String snippet) { }
    public record SearchResponse(List<SearchHit> hits) { }
    public record FieldError(String field, String message) { }
    public record ErrorResponse(String code, String message, List<FieldError> fieldErrors) { }
}

