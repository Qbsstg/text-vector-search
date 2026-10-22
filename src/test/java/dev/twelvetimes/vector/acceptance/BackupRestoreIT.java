package dev.twelvetimes.vector.acceptance;

import dev.twelvetimes.vector.api.contract.ApiModels.SearchRequest;
import dev.twelvetimes.vector.document.DocumentQueryService;
import dev.twelvetimes.vector.persistence.KnowledgeMapper;
import dev.twelvetimes.vector.persistence.TaskTerminalMapper;
import dev.twelvetimes.vector.persistence.TerminalSnapshot;
import dev.twelvetimes.vector.search.SearchService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 只在JUnit临时绝对目录做等价冷复制和真实应用恢复，不操作服务器或生产数据。 */
class BackupRestoreIT {
    @TempDir Path directory;

    @Test
    void 真实B1检索失效正常关闭冷复制校验后独立目录恢复保持全部状态() throws Exception {
        Path source = directory.resolve("original");
        Path backup = directory.resolve("backup");
        Path restore = directory.resolve("restore");
        TerminalSnapshot kept;
        TerminalSnapshot invalid;
        java.time.Instant invalidatedAt;
        String keptTask;
        String invalidTask;
        try (var env = new B4QaEnvironment(source, true, true, null)) {
            env.realDocument("kept", "ababa", null);
            env.realDocument("invalid", "", null);
            assertEquals(2, env.bean(SearchService.class).search(new SearchRequest("", 100, null)).hits().size());
            var changed = env.bean(DocumentQueryService.class).invalidate("invalid");
            invalidatedAt = changed.invalidatedAt();
            assertEquals(java.util.List.of("kept"), B4SearchIT.ids(env.bean(SearchService.class).search(new SearchRequest("ababa", 1, null))));
            keptTask = env.bean(KnowledgeMapper.class).findDocument("kept").taskId();
            invalidTask = env.bean(KnowledgeMapper.class).findDocument("invalid").taskId();
            kept = env.bean(TaskTerminalMapper.class).snapshot(keptTask);
            invalid = env.bean(TaskTerminalMapper.class).snapshot(invalidTask);
            assertEquals(2, env.hits("kept"));
            assertEquals(1, env.hits("invalid"));
        }
        // try关闭整个应用及Hikari后才读主文件；不在活动H2上复制。
        Path database = source.resolve("knowledge.mv.db");
        String originalHash = hash(database);
        Files.createDirectory(backup, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.copy(database, backup.resolve("knowledge.mv.db"));
        Files.setPosixFilePermissions(backup.resolve("knowledge.mv.db"), PosixFilePermissions.fromString("rw-------"));
        assertEquals(originalHash, hash(backup.resolve("knowledge.mv.db")));
        assertEquals(originalHash, hash(database), "冷复制不能改动源文件");
        assertThrowsExistingDestination(database, backup.resolve("knowledge.mv.db"));
        Files.createDirectory(restore, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------")));
        Files.copy(backup.resolve("knowledge.mv.db"), restore.resolve("knowledge.mv.db"));
        assertEquals(originalHash, hash(restore.resolve("knowledge.mv.db")));
        // 哈希在打开恢复库之前比较；真正H2重开可能合法更新文件元数据。
        try (var restored = new B4QaEnvironment(restore, true, true, null)) {
            var keptAgain = restored.bean(TaskTerminalMapper.class).snapshot(keptTask);
            var invalidAgain = restored.bean(TaskTerminalMapper.class).snapshot(invalidTask);
            assertSameTerminal(kept, keptAgain);
            assertSameTerminal(invalid, invalidAgain);
            assertEquals(2, restored.hits("kept"));
            assertEquals(1, restored.hits("invalid"));
            assertFalse(restored.bean(KnowledgeMapper.class).findDocument("kept").invalidated());
            assertTrue(restored.bean(KnowledgeMapper.class).findDocument("invalid").invalidated());
            assertEquals(invalidatedAt, restored.bean(KnowledgeMapper.class).findDocument("invalid").invalidatedAt());
            assertEquals("ababa", restored.bean(KnowledgeMapper.class).findDocument("kept").text());
            assertEquals("", restored.bean(KnowledgeMapper.class).findDocument("invalid").text());
            assertEquals(0, restored.bean(KnowledgeMapper.class).countPending());
        }
        assertEquals(originalHash, hash(backup.resolve("knowledge.mv.db")), "恢复打开的是新目录，备份原件保持不变");
        assertEquals(originalHash, hash(database), "独立恢复不替换源库");
        B4Evidence.record("FINAL_BACKUP_RESTORE", "databaseSHA256=" + originalHash
                + ";sourcePreserved=true;backupPreserved=true;keptHitCount=2;invalidHitCount=1;invalidatedAt=" + invalidatedAt);
    }

    private static void assertSameTerminal(TerminalSnapshot expected, TerminalSnapshot actual) {
        assertEquals(expected.taskId(), actual.taskId());
        assertEquals(expected.status(), actual.status());
        assertEquals(expected.runToken(), actual.runToken());
        assertEquals(expected.taskVersion(), actual.taskVersion());
        assertEquals(expected.documentVersion(), actual.documentVersion());
        assertEquals(expected.finishedAt(), actual.finishedAt());
        assertEquals(expected.vectorCompletedAt(), actual.vectorCompletedAt());
        assertArrayEquals(expected.vectorBytes(), actual.vectorBytes());
    }

    private static void assertThrowsExistingDestination(Path source, Path existing) {
        org.junit.jupiter.api.Assertions.assertThrows(java.nio.file.FileAlreadyExistsException.class, () -> Files.copy(source, existing));
    }

    private static String hash(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
