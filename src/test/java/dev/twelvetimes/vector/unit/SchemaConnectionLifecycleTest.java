package dev.twelvetimes.vector.unit;

import java.nio.file.Path;
import java.sql.DriverManager;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 约束必须独立于建表连接的寿命。连接池通常在约二十三至三十分钟回收首个连接，
 * 此处只显式结束建表连接，保留另一个连接维持同一数据库实例，避免整库重开掩盖错误。
 */
class SchemaConnectionLifecycleTest {
    @TempDir Path directory;

    @Test
    void 建表连接关闭后其他连接仍能通过冻结约束提交文档与任务() throws Exception {
        String url = "jdbc:h2:file:" + directory.toAbsolutePath().resolve("knowledge")
                + ";WRITE_DELAY=0;DB_CLOSE_ON_EXIT=FALSE";
        try (var initializer = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(initializer, new ClassPathResource("schema.sql"));
            try (var keeper = DriverManager.getConnection(url, "sa", "")) {
                // 必须在keeper仍存活时关闭initializer；若同时关闭全部连接，会重新加载约束而漏测。
                initializer.close();
                assertFalse(keeper.isClosed());
                assertTrue(keeper.isValid(2), "连接与只读健康仍可能正常，不能代替真实任务写入");
                keeper.setAutoCommit(false);
                Instant submitted = Instant.now();
                String taskId = UUID.randomUUID().toString();
                try (var document = keeper.prepareStatement("""
                        INSERT INTO documents(document_id,raw_text,channel,submitted_at) VALUES(?,?,?,?)
                        """)) {
                    document.setString(1, "connection-lifecycle");
                    document.setString(2, "连接回收回归");
                    document.setString(3, "default");
                    document.setObject(4, submitted);
                    assertEquals(1, document.executeUpdate());
                }
                try (var task = keeper.prepareStatement("""
                        INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at)
                        VALUES(?,?,'QUEUED',?,?)
                        """)) {
                    task.setString(1, taskId);
                    task.setString(2, "connection-lifecycle");
                    task.setString(3, "char12-sha256-r32-nfkc-root-ws-v1");
                    task.setObject(4, submitted);
                    assertEquals(1, task.executeUpdate());
                }
                keeper.commit();
                try (var query = keeper.createStatement();
                     var result = query.executeQuery("""
                             SELECT d.raw_text,t.task_id,t.status,d.hit_count
                             FROM documents d JOIN embedding_tasks t ON d.document_id=t.document_id
                             WHERE d.document_id='connection-lifecycle'
                             """)) {
                    assertTrue(result.next());
                    assertEquals("连接回收回归", result.getString(1));
                    assertEquals(taskId, result.getString(2));
                    assertEquals("QUEUED", result.getString(3));
                    assertEquals(0L, result.getLong(4));
                    assertFalse(result.next());
                }
            }
        }
    }
}
