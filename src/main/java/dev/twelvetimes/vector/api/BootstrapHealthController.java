package dev.twelvetimes.vector.api;

import dev.twelvetimes.vector.health.BusinessReadiness;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** 健康由只读运行检查推导；业务就绪不代表UI或正式发布已经获批。 */
@RestController
public class BootstrapHealthController {
    private final BusinessReadiness readiness;

    public BootstrapHealthController(BusinessReadiness readiness) {
        this.readiness = readiness;
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> health() {
        BusinessReadiness.Snapshot snapshot = readiness.inspect();
        boolean ready = snapshot.businessReady();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", ready ? "UP" : "DOWN");
        body.put("stage", "B4");
        body.put("businessReady", ready);
        body.put("taskReady", snapshot.taskReady());
        body.put("taskState", snapshot.taskState().name());
        // 算法实例无法给出版本时允许null，避免非就绪探针因空值触发额外的500响应。
        body.put("embeddingVersion", snapshot.embeddingVersion());
        body.put("checks", Map.of("databaseReady", snapshot.databaseReady(),
                "databaseWritable", snapshot.databaseWritable(), "dispatcherReady", snapshot.dispatcherReady(),
                "algorithmReady", snapshot.algorithmReady(), "searchReady", snapshot.searchReady(),
                "routesReady", snapshot.routesReady()));
        return ResponseEntity.status(ready ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE).body(body);
    }
}
