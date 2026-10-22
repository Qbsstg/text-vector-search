package dev.twelvetimes.vector.config;

import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskReadiness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 可选B3处理器缺省为空；不注册占位处理器，避免生产任务误入RUNNING。 */
@Configuration(proxyBeanMethods = false)
public class TaskDispatchConfiguration {
    @Bean
    public TaskDispatcher taskDispatcher(KnowledgeMapper mapper, TaskClaimService claims, TaskReadiness readiness,
                                          VectorProperties properties, ObjectProvider<TaskExecutionHandler> handlers) {
        return new TaskDispatcher(mapper, claims, readiness, properties, handlers.getIfAvailable());
    }
}
