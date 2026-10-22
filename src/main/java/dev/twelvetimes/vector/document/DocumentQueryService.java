package dev.twelvetimes.vector.document;

import dev.twelvetimes.vector.api.contract.ApiModels.DocumentDetail;
import dev.twelvetimes.vector.api.contract.ApiModels.DocumentPage;
import dev.twelvetimes.vector.api.contract.ApiModels.Invalidation;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.persistence.DocumentRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/** 管理读取不增加命中；失效只更新两个失效字段并保留首次提交时间。 */
@Service
public final class DocumentQueryService {
    private final KnowledgeMapper mapper;
    private final RequestValidator validator;
    private final TransactionTemplate transaction;
    private final TaskReadiness readiness;

    public DocumentQueryService(KnowledgeMapper mapper, RequestValidator validator, PlatformTransactionManager manager, TaskReadiness readiness) {
        this.mapper = mapper;
        this.validator = validator;
        this.readiness = readiness;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public DocumentDetail detail(String id) { return DocumentViews.detail(required(validator.documentId(id))); }

    public DocumentPage list(int page, int size, String channel, Boolean invalidated) {
        validator.page(Integer.toString(page));
        validator.size(Integer.toString(size));
        String selectedChannel = validator.channel(channel);
        long offset = (long) page * size;
        return new DocumentPage(mapper.list(selectedChannel, invalidated, size, offset).stream()
                .map(DocumentViews::summary).toList(), page, size, mapper.countFiltered(selectedChannel, invalidated));
    }

    public Invalidation invalidate(String id) {
        validator.documentId(id);
        try (var lease = readiness.enterWrite()) {
            return transaction.execute(status -> {
                required(id);
                mapper.invalidate(id, Instant.now());
                DocumentRow current = required(id);
                return new Invalidation(id, current.invalidated(), current.invalidatedAt());
            });
        }
    }

    private DocumentRow required(String id) {
        DocumentRow row = mapper.findDocument(id);
        if (row == null) { throw new BusinessException(404, "DOCUMENT_NOT_FOUND", "文档不存在"); }
        return row;
    }
}
