package dev.twelvetimes.vector.search;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchHit;
import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.api.contract.ApiModels.SearchResponse;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.embedding.VectorCodec;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** 同步精确检索编排：CPU无事务/连接/关闭许可，单个搜索许可覆盖计算至计数真实提交。 */
@Service
public final class SearchService {
    private final RequestValidator validator;
    private final TextEmbedder embedder;
    private final SearchCandidateSource candidates;
    private final SearchHitCounter counter;
    private final TaskReadiness readiness;
    private final int maxDocuments;
    private final Semaphore permits;

    public SearchService(RequestValidator validator, TextEmbedder embedder, SearchCandidateSource candidates,
                           SearchHitCounter counter, TaskReadiness readiness, VectorProperties properties) {
        this.validator = validator;
        this.embedder = embedder;
        this.candidates = candidates;
        this.counter = counter;
        this.readiness = readiness;
        maxDocuments = properties.maxDocuments();
        permits = new Semaphore(properties.searchPermits());
    }

    public SearchResponse search(SearchRequest input) {
        SearchRequest request = validator.search(input);
        // REQUIRES_NEW返回后会恢复外层事务，因此入口必须拒绝它，才能保证后续CPU不持外层连接。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "搜索不能嵌入外层事务");
        }
        readiness.requireAccepting();
        if (!permits.tryAcquire()) { throw new BusinessException(429, "SEARCH_BUSY", "搜索繁忙，请稍后手动重试"); }
        try {
            float[] queryVector = embedder.embed(request.text());
            List<SearchCandidate> snapshot = candidates.load(embedder.version(), request.channel());
            if (snapshot.size() > maxDocuments) { throw unavailable(); }
            Map<String, SearchCandidate> byId = new HashMap<>();
            for (SearchCandidate candidate : snapshot) {
                if (byId.put(candidate.documentId(), candidate) != null) { throw unavailable(); }
            }
            List<ScoredDocument> selected;
            try {
                // 流只作为一次性Iterable供冻结TopKSelector逐项评分，不创建全部分数集合或全排序。
                Iterable<ScoredDocument> scores = () -> snapshot.stream().map(candidate -> new ScoredDocument(candidate.documentId(),
                        CosineSimilarity.score(queryVector, VectorCodec.decode(candidate.vectorBytes())))).iterator();
                selected = TopKSelector.select(scores, request.topK());
            } catch (IllegalArgumentException exception) {
                // 坏向量不是“没有命中”；必须失败且不能给任何文档增加计数。
                throw unavailable();
            }
            List<SearchHit> hits = selected.stream().map(score -> {
                SearchCandidate candidate = byId.get(score.documentId());
                return new SearchHit(score.documentId(), candidate.channel(), score.score(), snippet(candidate.prefix()));
            }).toList();
            readiness.requireAccepting();
            if (!hits.isEmpty()) { counter.increment(selected.stream().map(ScoredDocument::documentId).toList()); }
            return new SearchResponse(hits);
        } finally {
            permits.release();
        }
    }

    private static String snippet(String prefix) {
        int count = prefix.codePointCount(0, prefix.length());
        return count <= 200 ? prefix : prefix.substring(0, prefix.offsetByCodePoints(0, 200));
    }

    private static BusinessException unavailable() {
        return new BusinessException(503, "DATABASE_UNAVAILABLE", "候选文档数据不符合检索约束");
    }
}
