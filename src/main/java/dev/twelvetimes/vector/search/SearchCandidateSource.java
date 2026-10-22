package dev.twelvetimes.vector.search;

import java.util.List;

/** 返回后已释放短读取事务和连接；测试可以包裹真实来源控制快照与后续失效的交错。 */
@FunctionalInterface
public interface SearchCandidateSource {
    List<SearchCandidate> load(String version, String channel);
}
