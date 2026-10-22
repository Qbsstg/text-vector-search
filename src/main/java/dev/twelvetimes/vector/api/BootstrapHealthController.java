package dev.twelvetimes.vector.api;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Infrastructure health only. Business readiness/recovery gating is a later backend task. */
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
            // Keep database details out of the unauthenticated local health response.
        }
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("status", "DOWN", "stage", "C0", "businessReady", false));
    }
}

