package dev.twelvetimes.vector.acceptance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** 仅保存合成测试事件；源码SHA与当时未提交测试状态分开，不冒充最终发布SHA。 */
final class B4Evidence {
    private static String sourceSha;

    private B4Evidence() { }

    static synchronized String sourceSha() {
        if (sourceSha == null) {
            try {
                Process git = new ProcessBuilder("git", "rev-parse", "HEAD").start();
                if (!git.waitFor(5, TimeUnit.SECONDS) || git.exitValue() != 0) { throw new AssertionError("无法记录源码SHA"); }
                sourceSha = new String(git.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (Exception exception) { throw new AssertionError(exception); }
        }
        return sourceSha;
    }

    static void record(String scenario, String detail) {
        try {
            Path root = Path.of("target/b4-evidence");
            Files.createDirectories(root);
            Files.writeString(root.resolve(scenario + "-" + UUID.randomUUID() + ".txt"),
                    "sourceSHA=" + sourceSha() + ";tests=本轮测试工作树\n" + detail + "\n", StandardCharsets.UTF_8);
        } catch (Exception exception) { throw new AssertionError(exception); }
    }
}
