package dev.twelvetimes.vector.config;

import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.task.TaskClaimService;
import dev.twelvetimes.vector.task.TaskDispatcher;
import dev.twelvetimes.vector.task.TaskExecutionHandler;
import dev.twelvetimes.vector.task.TaskReadiness;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 生产由B3配置提供真实处理器；仅显式隔离的B2测试组成允许缺省，不注册空实现。 */
@Configuration(proxyBeanMethods = false)
public class TaskDispatchConfiguration {
    @Bean
    public TaskDispatcher taskDispatcher(KnowledgeMapper mapper, TaskClaimService claims, TaskReadiness readiness,
                                          VectorProperties properties, ObjectProvider<TaskExecutionHandler> handlers) {
        return new TaskDispatcher(mapper, claims, readiness, properties, handlers.getIfAvailable());
    }
}
