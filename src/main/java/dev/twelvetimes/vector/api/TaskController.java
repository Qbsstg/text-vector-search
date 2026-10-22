package dev.twelvetimes.vector.api;

import dev.twelvetimes.vector.api.contract.ApiModels.TaskView;
import dev.twelvetimes.vector.task.TaskQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 任务查询使用真实持久状态，不因轮询失败伪造FAILED。 */
@RestController
@RequestMapping("/api/tasks")
public final class TaskController {
    private final TaskQueryService query;
    public TaskController(TaskQueryService query) { this.query = query; }

    @GetMapping("/{taskId}")
    public TaskView get(@PathVariable String taskId) { return query.get(taskId); }
}
