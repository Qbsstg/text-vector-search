package dev.twelvetimes.vector.search;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.config.VectorProperties;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** 全量读取有界候选快照；只在数据库阶段持关闭许可，返回后不持连接或事务。 */
@Service
public final class SearchCandidateReader implements SearchCandidateSource {
    private final SearchMapper mapper;
    private final TaskReadiness readiness;
    private final int maxDocuments;
    private final TransactionTemplate transaction;

    public SearchCandidateReader(SearchMapper mapper, TaskReadiness readiness, VectorProperties properties,
                                   PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.readiness = readiness;
        maxDocuments = properties.maxDocuments();
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transaction.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        transaction.setReadOnly(true);
    }

    @Override
    public List<SearchCandidate> load(String version, String channel) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "搜索读取不能嵌入外层事务");
        }
        try (var lease = readiness.enterWrite()) {
            return transaction.execute(status -> {
                if (mapper.countDocuments() > maxDocuments) { throw capacity(); }
                // 多读一条只用于检测非法超容量；一旦超过就失败，绝不返回截断的Top-K。
                List<SearchCandidate> candidates = mapper.candidates(version, channel, maxDocuments + 1);
                if (candidates.size() > maxDocuments) { throw capacity(); }
                return List.copyOf(candidates);
            });
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(503, "DATABASE_UNAVAILABLE", "暂时无法读取候选文档");
        }
    }

    private static BusinessException capacity() {
        return new BusinessException(503, "DATABASE_UNAVAILABLE", "文档数据超过运行容量，不能返回不完整检索");
    }
}
