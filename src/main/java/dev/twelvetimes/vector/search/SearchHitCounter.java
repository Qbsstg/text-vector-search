package dev.twelvetimes.vector.search;

import java.util.List;

/** 只有最终Top-K的ID进入计数端口；正常返回表示全部计数已经物理提交。 */
@FunctionalInterface
public interface SearchHitCounter {
    void increment(List<String> documentIds);
}
