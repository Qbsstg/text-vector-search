package dev.twelvetimes.vector.search;

import dev.twelvetimes.vector.api.error.BusinessException;
import dev.twelvetimes.vector.persistence.SearchMapper;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 最终命中按固定ID顺序原子自增，所有K项共享一次真实提交。
 * 没有请求幂等标识，因此提交未知不查询累计值猜测、不重放增量、不自动重试。
 */
@Service
public final class SearchHitCountService implements SearchHitCounter {
    private final SearchMapper mapper;
    private final TaskReadiness readiness;
    private final TransactionTemplate transaction;

    public SearchHitCountService(SearchMapper mapper, TaskReadiness readiness, PlatformTransactionManager manager) {
        this.mapper = mapper;
        this.readiness = readiness;
        transaction = new TransactionTemplate(manager);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public void increment(List<String> documentIds) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new BusinessException(503, "SERVICE_NOT_READY", "搜索计数不能嵌入外层事务");
        }
        readiness.requireAccepting();
        if (documentIds.isEmpty()) { return; }
        List<String> ordered = new ArrayList<>(documentIds);
        if (ordered.stream().anyMatch(id -> id == null)) { throw new IllegalArgumentException("最终命中ID不能为null"); }
        ordered.sort(String::compareTo);
        for (int index = 0; index < ordered.size(); index++) {
            if (ordered.get(index) == null || (index > 0 && ordered.get(index).equals(ordered.get(index - 1)))) {
                throw new IllegalArgumentException("最终命中ID必须非null且唯一");
            }
        }
        try (var lease = readiness.enterWrite()) {
            AtomicBoolean commitAttempted = new AtomicBoolean();
            try {
                transaction.executeWithoutResult(status -> {
                    for (String id : ordered) {
                        // 不按失效/渠道再次过滤，否则快照后失效会让已选中的命中漏计。
                        if (mapper.incrementHit(id) != 1) {
                            throw new BusinessException(503, "DATABASE_UNAVAILABLE", "命中计数写入不完整");
                        }
                    }
                    commitAttempted.set(true);
                });
            } catch (RuntimeException exception) {
                if (commitAttempted.get() || exception instanceof TransactionException) {
                    readiness.block("搜索命中计数提交结果无法确认");
                    throw new BusinessException(503, "RESULT_UNKNOWN", "本次检索可能已完成并计数，请勿自动重试");
                }
                if (exception instanceof BusinessException business) { throw business; }
                // 回调内失败且TransactionTemplate已经完成回滚；整个结果不能报成功。
                throw new BusinessException(503, "DATABASE_UNAVAILABLE", "本次检索计数未能保存");
            }
        }
    }
}
