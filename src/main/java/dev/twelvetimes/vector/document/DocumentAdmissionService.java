package dev.twelvetimes.vector.document;

import dev.twelvetimes.vector.api.contract.ApiModels.Submission;
import dev.twelvetimes.vector.api.contract.ApiModels.SubmitDocumentRequest;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.DocumentRow;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 单实例接收锁覆盖容量检查、两表写入与真正提交，不依赖代理方法退出后的提交。 */
@Service
public final class DocumentAdmissionService {
    private final Object admissionLock = new Object();
    private final KnowledgeMapper mapper;
    private final RequestValidator validator;
    private final VectorProperties properties;
    private final TextEmbedder embedder;
    private final TaskReadiness readiness;
    private final TransactionTemplate transaction;

    public DocumentAdmissionService(KnowledgeMapper mapper, RequestValidator validator, VectorProperties properties,
                                    TextEmbedder embedder, TaskReadiness readiness, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.validator = validator;
        this.properties = properties;
        this.embedder = embedder;
        this.readiness = readiness;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public Submission submit(SubmitDocumentRequest input) {
        SubmitDocumentRequest request = validator.submission(input);
        // 拒绝未知外层事务，而不是误把参与事务的回调结束当成物理提交。
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "接收操作不能在外层事务中执行");
        }
        synchronized (admissionLock) {
            try (var lease = readiness.enterWrite()) {
                String taskId = UUID.randomUUID().toString();
                Instant submitted = Instant.now();
                AtomicBoolean commitAttempted = new AtomicBoolean();
                try {
                    transaction.executeWithoutResult(status -> {
                        if (mapper.findDocument(request.documentId()) != null) {
                            throw new BusinessException(409, "DOCUMENT_EXISTS", "文档ID已存在，不覆盖原文");
                        }
                        if (mapper.countDocuments() >= properties.maxDocuments()
                                || mapper.countPending() >= properties.maxPendingTasks()) {
                            throw new BusinessException(429, "CAPACITY_EXCEEDED", "文档或待处理任务容量已满");
                        }
                        if (mapper.insertDocument(request.documentId(), request.text(), request.channel(), submitted) != 1
                                || mapper.insertTask(taskId, request.documentId(), embedder.version(), submitted) != 1) {
                            throw new IllegalStateException("两表接收写入行数异常");
                        }
                        commitAttempted.set(true);
                    });
                } catch (BusinessException exception) {
                    throw exception;
                } catch (RuntimeException exception) {
                    if (commitAttempted.get() || exception instanceof TransactionException) {
                        // 提交后抛错仍可能已接收。用新事务按本次UUID核对，不能盲目重插或返回确定失败。
                        try {
                            DocumentRow confirmed = transaction.execute(status -> mapper.findTask(taskId));
                            if (confirmed != null && confirmed.documentId().equals(request.documentId())) {
                                return new Submission(taskId, request.documentId(), TaskStatus.QUEUED);
                            }
                        } catch (RuntimeException ignored) {
                            // 无法确认时保留任务与文档，阻止后续接收，交B3恢复诊断。
                        }
                        readiness.block("接收事务提交结果无法确认");
                        throw new BusinessException(503, "RESULT_UNKNOWN", "无法确认是否已接收，请按文档ID查询");
                    }
                    if (exception instanceof DataAccessException) {
                        throw new BusinessException(503, "DATABASE_UNAVAILABLE", "暂时无法保存文档");
                    }
                    throw exception;
                }
                return new Submission(taskId, request.documentId(), TaskStatus.QUEUED);
            }
        }
    }
}
