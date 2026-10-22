package dev.twelvetimes.vector.health;

import dev.twelvetimes.vector.task.TaskReadiness;

/** 内部只读运行事实探针；业务就绪不等于浏览器验收或正式发布已获批准。 */
public interface BusinessReadiness {
    Snapshot inspect();

    /** 每次调用的观察快照，不缓存为可自行修改的冗余就绪标记。 */
    record Snapshot(TaskReadiness.State taskState, boolean taskReady, boolean databaseReady,
                    boolean databaseWritable, boolean dispatcherReady, boolean algorithmReady,
                    boolean searchReady, boolean routesReady, String embeddingVersion) {
        public boolean businessReady() {
            return taskState == TaskReadiness.State.READY && taskReady && databaseReady && databaseWritable
                    && dispatcherReady && algorithmReady && searchReady && routesReady;
        }
    }
}
