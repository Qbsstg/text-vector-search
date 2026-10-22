package dev.twelvetimes.vector.task;

import dev.twelvetimes.vector.api.contract.ApiModels.TaskView;
import dev.twelvetimes.vector.api.contract.TaskStatus;
import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.api.validation.RequestValidator;
import dev.twelvetimes.vector.document.DocumentViews;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import org.springframework.stereotype.Service;

/** 四态直接来源于持久任务；只在成功时携带文档详情。 */
@Service
public final class TaskQueryService {
    private final KnowledgeMapper mapper;
    private final RequestValidator validator;

    public TaskQueryService(KnowledgeMapper mapper, RequestValidator validator) {
        this.mapper = mapper;
        this.validator = validator;
    }

    public TaskView get(String taskId) {
        var row = mapper.findTask(validator.taskId(taskId));
        if (row == null) { throw new BusinessException(404, "TASK_NOT_FOUND", "任务不存在"); }
        TaskStatus status = TaskStatus.valueOf(row.status());
        return new TaskView(row.taskId(), row.documentId(), status, row.submittedAt(), row.startedAt(),
                row.finishedAt(), row.errorCode(), row.errorMessage(),
                status == TaskStatus.SUCCEEDED ? DocumentViews.detail(row) : null);
    }
}
