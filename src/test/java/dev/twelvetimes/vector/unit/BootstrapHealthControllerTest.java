package dev.twelvetimes.vector.unit;

import dev.twelvetimes.vector.api.BootstrapHealthController;
import dev.twelvetimes.vector.task.TaskReadiness;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BootstrapHealthControllerTest {
    @Test
    void 恢复降级和停止健康拒绝且不抢占连接() {
        TaskReadiness readiness = new TaskReadiness(null);
        AtomicInteger borrows = new AtomicInteger();
        var controller = new BootstrapHealthController(dataSource(borrows, false), readiness);
        assertEquals(503, controller.health().getStatusCode().value());
        assertEquals("RECOVERING", controller.health().getBody().get("taskState"));
        readiness.block("仅测试降级");
        assertEquals(503, controller.health().getStatusCode().value());
        readiness.stopping();
        assertEquals(503, controller.health().getStatusCode().value());
        readiness.stopped();
        assertEquals(503, controller.health().getStatusCode().value());
        assertEquals(0, borrows.get());
    }

    @Test
    void 任务READY与数据库可用不冒称B4业务完成() {
        TaskReadiness readiness = new TaskReadiness(null);
        readiness.ready();
        AtomicInteger borrows = new AtomicInteger();
        var response = new BootstrapHealthController(dataSource(borrows, false), readiness).health();
        assertEquals(200, response.getStatusCode().value());
        assertEquals("B3", response.getBody().get("stage"));
        assertEquals("READY", response.getBody().get("taskState"));
        assertEquals(true, response.getBody().get("taskReady"));
        assertEquals(false, response.getBody().get("businessReady"));
        assertEquals(1, borrows.get());
    }

    @Test
    void 单次数据库探测失败不泄露细节也不擅自迁移门禁() {
        TaskReadiness readiness = new TaskReadiness(null);
        readiness.ready();
        var response = new BootstrapHealthController(dataSource(new AtomicInteger(), true), readiness).health();
        assertEquals(503, response.getStatusCode().value());
        assertEquals("DOWN", response.getBody().get("status"));
        assertEquals(false, response.getBody().get("taskReady"));
        assertEquals(TaskReadiness.State.READY, readiness.state());
    }

    /** 只替代本单测的连接探测边界，不模拟业务事务或恢复成功。 */
    private static DataSource dataSource(AtomicInteger borrows, boolean fail) {
        return (DataSource) Proxy.newProxyInstance(DataSource.class.getClassLoader(),
                new Class<?>[] {DataSource.class}, (proxy, method, args) -> {
                    if (method.getName().equals("getConnection")) {
                        borrows.incrementAndGet();
                        if (fail) { throw new SQLException("仅测试：不可向响应泄露的原因"); }
                        return Proxy.newProxyInstance(Connection.class.getClassLoader(),
                                new Class<?>[] {Connection.class}, (connection, call, parameters) -> {
                                    if (call.getName().equals("isValid")) { return true; }
                                    if (call.getName().equals("close")) { return null; }
                                    throw new UnsupportedOperationException(call.getName());
                                });
                    }
                    throw new UnsupportedOperationException(method.getName());
                });
    }
}
