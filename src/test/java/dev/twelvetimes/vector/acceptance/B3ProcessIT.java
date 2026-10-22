package dev.twelvetimes.vector.acceptance;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.sql.DriverManager;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/** 独立JVM实际接收操作系统信号；强杀与SIGTERM分开，不以普通异常或正常重启冒充。 */
class B3ProcessIT {
    private static final String GOLDEN = "ad82c864c2dec3476778eeaf5f6e83a64d5827b9f029f22737e7e33523125ad1";
    @TempDir Path directory;

    @ParameterizedTest
    @ValueSource(strings = {"QUEUED", "RUNNING", "TERMINAL_BEFORE_COMMIT", "TERMINAL_COMMITTED"})
    void 四检查点真实强杀后同库恢复真实B1且已成功不重做(String stage) throws Exception {
        String event = stage.equals("QUEUED") || stage.equals("RUNNING") ? stage + "_COMMITTED" : stage;
        Snapshot before;
        try (Child child = new Child(stage, directory)) {
            child.await(event);
            child.kill();
            // 必须确认旧pid退出，下面连接打开成功也独立证明文件锁已释放。
            assertFalse(ProcessHandle.of(child.process.pid()).map(ProcessHandle::isAlive).orElse(false));
            before = database(directory);
            child.note("强杀后快照=" + before);
            if (stage.equals("TERMINAL_COMMITTED")) {
                assertEquals("SUCCEEDED", before.status());
                assertEquals(GOLDEN, before.sha());
                assertNotNull(before.finished());
            } else {
                assertEquals(stage.equals("QUEUED") ? "QUEUED" : "RUNNING", before.status());
                assertNull(before.sha());
                assertNull(before.finished());
                assertNull(before.vectorTime());
            }
        }
        try (Child restarted = new Child("RESTART", directory)) {
            Map<String, String> completed = fields(restarted.await("RESTART_SUCCEEDED"));
            assertEquals(GOLDEN, completed.get("sha"));
            restarted.terminate(false);
            Snapshot after = database(directory);
            restarted.note("重启后快照=" + after);
            assertEquals("SUCCEEDED", after.status());
            assertEquals(GOLDEN, after.sha());
            assertEquals(after.finished(), after.vectorTime());
            assertEquals(before.taskId(), after.taskId());
            assertEquals(0, after.hitCount());
            assertFalse(after.invalidated());
            if (stage.equals("TERMINAL_COMMITTED")) {
                assertEquals(before, after, "已提交成功不能重做、换token或覆盖首次完成时间");
            } else {
                assertEquals(before.attempt() + 1, after.attempt());
                assertNotEquals(before.token(), after.token());
            }
        }
    }

    @Test
    void SIGTERM在有界等待内完成真实B1并正常关闭() throws Exception {
        try (Child child = new Child("SIGTERM_GRACEFUL", directory)) {
            assertEquals(GOLDEN, fields(child.await("CPU_READY")).get("sha"));
            child.signalTerm();
            child.note("发送SIGTERM");
            child.await("STOPPING");
            child.process.getOutputStream().write("RELEASE\n".getBytes(StandardCharsets.UTF_8));
            child.process.getOutputStream().flush();
            child.await("DATASOURCE_CLOSED");
            Map<String, String> snapshot = fields(child.await("SHUTDOWN_SNAPSHOT"));
            assertEquals("SUCCEEDED", snapshot.get("status"));
            assertEquals(GOLDEN, snapshot.get("sha"));
            child.waitExit();
            assertEquals("SUCCEEDED", database(directory).status());
        }
    }

    @Test
    void SIGTERM超时关闭数据源后放CPU不再访问SQL并可重启恢复() throws Exception {
        Snapshot before;
        try (Child child = new Child("SIGTERM_TIMEOUT", directory)) {
            assertEquals(GOLDEN, fields(child.await("CPU_READY")).get("sha"));
            child.signalTerm();
            child.note("发送SIGTERM，CPU仅在真实数据源关闭后释放");
            child.await("DATASOURCE_CLOSED");
            assertEquals("0", fields(child.await("LATE_SQL_COUNT")).get("count"));
            Map<String, String> persisted = fields(child.await("SHUTDOWN_SNAPSHOT"));
            assertEquals("RUNNING", persisted.get("status"));
            child.waitExit();
            before = database(directory);
            assertEquals("RUNNING", before.status());
            assertNull(before.sha());
        }
        try (Child restarted = new Child("RESTART", directory)) {
            restarted.await("RESTART_SUCCEEDED");
            restarted.terminate(false);
            Snapshot after = database(directory);
            assertEquals(GOLDEN, after.sha());
            assertEquals(before.attempt() + 1, after.attempt());
            assertNotEquals(before.token(), after.token());
            restarted.note("超时后恢复快照=" + after);
        }
    }

    record Snapshot(String taskId, String status, String token, int attempt, String finished, String vectorTime,
                    String sha, long hitCount, boolean invalidated) {
    }

    private static Snapshot database(Path path) throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:file:" + path.toAbsolutePath() + "/knowledge;WRITE_DELAY=0", "sa", "");
             var statement = connection.createStatement();
             var row = statement.executeQuery("SELECT t.task_id,t.status,t.run_token,t.attempt_count,t.finished_at,"
                     + "d.vector_completed_at,d.vector_bytes,d.hit_count,d.invalidated FROM documents d JOIN embedding_tasks t "
                     + "ON d.document_id=t.document_id WHERE d.document_id='crash'")) {
            assertTrue(row.next());
            byte[] bytes = row.getBytes(7);
            return new Snapshot(row.getString(1), row.getString(2), row.getString(3), row.getInt(4), row.getString(5),
                    row.getString(6), bytes == null ? null : HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                    row.getLong(8), row.getBoolean(9));
        }
    }

    private static Map<String, String> fields(String event) {
        Map<String, String> result = new HashMap<>();
        String detail = event.split("\\|", 4)[3];
        for (String item : detail.split(";")) {
            int equal = item.indexOf('=');
            if (equal > 0) {
                result.put(item.substring(0, equal), item.substring(equal + 1));
            }
        }
        return result;
    }

    /** 持续读取子进程输出避免管道塞满；事件与完整日志同时落到被忽略的target目录。 */
    static final class Child implements AutoCloseable {
        final Process process;
        private final Path evidence;
        private final BlockingQueue<String> events = new LinkedBlockingQueue<>();
        private final Thread reader;

        Child(String mode, Path db) throws Exception {
            Path root = Path.of("target", "b3-process-evidence");
            Files.createDirectories(root);
            evidence = root.resolve(mode + "-" + UUID.randomUUID() + ".log");
            String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
            String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            process = new ProcessBuilder(java, "-cp", classpath, B3ProcessProbe.class.getName(), mode,
                    db.toAbsolutePath().toString()).redirectErrorStream(true).start();
            note("命令=" + java + " -cp <当前测试classpath> " + B3ProcessProbe.class.getName() + " " + mode
                    + " " + db.toAbsolutePath() + ";pid=" + process.pid() + ";SHA=" + sourceSha());
            reader = new Thread(() -> {
                try (var lines = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                    String line;
                    while ((line = lines.readLine()) != null) {
                        note(line);
                        if (line.startsWith("QA_EVENT|")) {
                            events.add(line);
                        }
                    }
                } catch (Exception error) {
                    events.add("READER_FAILURE:" + error);
                }
            }, "qa-child-log-" + process.pid());
            reader.setDaemon(true);
            reader.start();
        }

        String await(String expected) throws Exception {
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(35);
            while (System.nanoTime() < deadline) {
                String event = events.poll(1, TimeUnit.SECONDS);
                if (event == null) {
                    assertTrue(process.isAlive(), "子进程提前退出，证据=" + evidence);
                    continue;
                }
                assertFalse(event.contains("HOOK_FAILURE") || event.startsWith("READER_FAILURE"), event);
                if (event.split("\\|", 4)[2].equals(expected)) {
                    return event;
                }
            }
            return fail("事件超时：" + expected + "，证据=" + evidence);
        }

        synchronized void note(String line) throws IOException {
            Files.writeString(evidence, line + System.lineSeparator(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        }

        void kill() throws Exception {
            note("发送SIGKILL/destroyForcibly，pid=" + process.pid());
            process.destroyForcibly();
            waitExit();
            assertEquals(137, process.exitValue(), "本机Unix真实强杀退出码必须独立记录");
        }

        void terminate(boolean force) throws Exception {
            if (force) {
                kill();
            } else {
                signalTerm();
                note("发送SIGTERM，pid=" + process.pid());
                waitExit();
            }
        }

        void signalTerm() throws Exception {
            // 外部kill保留标准输入输出管道，使关闭hook事件和CPU释放命令仍可交互。
            Process signal = new ProcessBuilder("/bin/kill", "-TERM", Long.toString(process.pid())).start();
            assertTrue(signal.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, signal.exitValue());
        }

        void waitExit() throws Exception {
            assertTrue(process.waitFor(20, TimeUnit.SECONDS), "子进程退出超时：" + evidence);
            reader.join(5000);
            note("实际退出：pid=" + process.pid() + ";exit=" + process.exitValue());
        }

        @Override
        public void close() throws Exception {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS));
                reader.join(5000);
                note("失败清理子进程：exit=" + process.exitValue());
            }
        }

        private static String sourceSha() throws Exception {
            Process git = new ProcessBuilder("git", "rev-parse", "HEAD").start();
            assertTrue(git.waitFor(5, TimeUnit.SECONDS));
            assertEquals(0, git.exitValue());
            return new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        }
    }
}
