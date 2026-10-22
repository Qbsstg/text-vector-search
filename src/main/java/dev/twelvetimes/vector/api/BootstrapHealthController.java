package dev.twelvetimes.vector.api;

import dev.twelvetimes.vector.task.TaskReadiness;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康同时观察数据库与任务门禁；任务READY不代表尚未实现的B4已完成。 */
@RestController
public class BootstrapHealthController {
    private final DataSource dataSource;
    private final TaskReadiness readiness;

    public BootstrapHealthController(DataSource dataSource, TaskReadiness readiness) {
        this.dataSource = dataSource;
        this.readiness = readiness;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        TaskReadiness.State state = readiness.state();
        boolean taskReady = false;
        // 恢复和停止阶段不额外借连接，避免健康探测占用恢复所需的有限连接池。
        if (state == TaskReadiness.State.READY) {
            try (Connection connection = dataSource.getConnection()) {
                taskReady = connection.isValid(2);
            } catch (SQLException ignored) {
                // 响应只表示本次探测结果，不泄露数据库细节或用单次失败擅自恢复门禁。
            }
        }
        Map<String, Object> body = Map.of("status", taskReady ? "UP" : "DOWN", "stage", "B3",
                "businessReady", false, "taskReady", taskReady, "taskState", state.name());
        return ResponseEntity.status(taskReady ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}

