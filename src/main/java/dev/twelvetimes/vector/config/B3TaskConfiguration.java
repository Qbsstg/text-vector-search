package dev.twelvetimes.vector.config;

import dev.twelvetimes.vector.embedding.TextEmbedder;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskRecoveryMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.task.ProductionTaskExecutionHandler;
import dev.twelvetimes.vector.task.ReadinessFilter;
import dev.twelvetimes.vector.task.TaskReadiness;
import dev.twelvetimes.vector.task.TaskRecoveryService;
import dev.twelvetimes.vector.task.TaskTerminalService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import tools.jackson.databind.ObjectMapper;

/** 生产B3组成统一装配恢复、真实算法处理器与API门禁；阶段B2测试显式隔离此配置。 */
@Configuration(proxyBeanMethods = false)
public class B3TaskConfiguration {
    @Bean
    public TaskTerminalService taskTerminalService(TaskTerminalMapper mapper, TaskReadiness readiness, PlatformTransactionManager manager) {
        return new TaskTerminalService(mapper, readiness, readiness.writeBarrier(), manager);
    }

    @Bean
    public ProductionTaskExecutionHandler taskExecutionHandler(TextEmbedder embedder, TaskTerminalService terminals) {
        return new ProductionTaskExecutionHandler(embedder, terminals);
    }

    @Bean
    public TaskRecoveryService taskRecoveryService(TaskRecoveryMapper recovery, KnowledgeMapper tasks,
                                                   TaskReadiness readiness, PlatformTransactionManager manager) {
        return new TaskRecoveryService(recovery, tasks, readiness, manager);
    }

    @Bean
    public ReadinessFilter readinessFilter(TaskReadiness readiness, ObjectMapper mapper) {
        return new ReadinessFilter(readiness, mapper);
    }
}
