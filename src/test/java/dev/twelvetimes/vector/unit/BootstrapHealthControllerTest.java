package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.BootstrapHealthController;
import dev.twelvetimes.vector.health.BusinessReadiness.Snapshot;
import dev.twelvetimes.vector.task.TaskReadiness.State;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Controller只验HTTP映射；数据库借用/关闭竞态由真实探针单测和独立IT负责。 */
class BootstrapHealthControllerTest {
    private static final String VERSION = "char12-sha256-r32-nfkc-root-ws-v1";

    @Test
    void 全部真实检查就绪才返回业务READY且实现阶段为B4() {
        var response = new BootstrapHealthController(() -> snapshot(State.READY, allReady(), VERSION)).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("UP", response.getBody().get("status"));
        assertEquals("B4", response.getBody().get("stage"));
        assertEquals("READY", response.getBody().get("taskState"));
        assertEquals(true, response.getBody().get("taskReady"));
        assertEquals(true, response.getBody().get("businessReady"));
        assertEquals(VERSION, response.getBody().get("embeddingVersion"));
    }

    @Test
    void 任一检查失败不能被硬编码成功掩盖() {
        for (int failed = 0; failed < 7; failed++) {
            boolean[] flags = allReady();
            flags[failed] = false;
            var response = new BootstrapHealthController(() -> snapshot(State.READY, flags, null)).health();
            assertEquals(503, response.getStatusCode().value());
            assertEquals("DOWN", response.getBody().get("status"));
            assertEquals(false, response.getBody().get("businessReady"));
            assertEquals(null, response.getBody().get("embeddingVersion"));
        }
    }

    @Test
    void 非READY即使其它检查均真也不得报告业务就绪() {
        for (State state : State.values()) {
            if (state == State.READY) { continue; }
            var response = new BootstrapHealthController(() -> snapshot(state, allReady(), VERSION)).health();
            assertEquals(503, response.getStatusCode().value());
            assertEquals(state.name(), response.getBody().get("taskState"));
            assertEquals(false, response.getBody().get("businessReady"));
        }
    }

    private static boolean[] allReady() {
        boolean[] result = new boolean[7];
        Arrays.fill(result, true);
        return result;
    }

    private static Snapshot snapshot(State state, boolean[] flags, String version) {
        return new Snapshot(state, flags[0], flags[1], flags[2], flags[3], flags[4], flags[5], flags[6], version);
    }
}
