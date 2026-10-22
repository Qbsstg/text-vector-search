package dev.twelvetimes.vector.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 仅检查基础连接；业务就绪和恢复门禁属于后续后端阶段。 */
@RestController
public class BootstrapHealthController {
    private final DataSource dataSource;

    public BootstrapHealthController(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) {
                return ResponseEntity.ok(Map.of("status", "UP", "stage", "C0", "businessReady", false));
            }
        } catch (SQLException ignored) {
            // 本地健康响应也不泄露数据库异常细节。
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "stage", "C0", "businessReady", false));
    }
}

