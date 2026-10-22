import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 仅核验已停机冷复制、完整校验并封存的新副本，不打开生产原件。
 * 密码只在进程内读取；只运行参数绑定的SELECT，不运行DDL、迁移或写SQL。
 */
public final class ColdSnapshot {
    private static final String SOURCE_SHA = "d016d6e8bb431ff17ba81f8977d31a1b96f51f7f";
    private static final String JAR_SHA = "64bc9c0ba187b6779622a7ddab5beb4b50152424e3926c55b76249c98b609b50";
    private static final String PREFIX = "FINAL-RELEASE-";
    private static final String H2_SHA = "29b70e427cc1c40cdc376283adbb0cc62853073797bb5fe5761f81fe73d57ce0";
    private static final Set<PosixFilePermission> FILE_READ_ONLY = Set.of(PosixFilePermission.OWNER_READ);
    private static final Set<PosixFilePermission> DIRECTORY_READ_ONLY = Set.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_EXECUTE);

    private ColdSnapshot() { }

    /** 参数只有副本目录与受限凭据文件路径，不接受密码、JDBC URL或任意查询。 */
    public static void main(String[] args) {
        try {
            if (args.length != 2) {
                throw new IllegalArgumentException("仅接受冷副本目录和受限env文件路径");
            }
            inspect(args[0], args[1]);
        } catch (Exception exception) {
            // JDBC消息可能携带URL等配置，故只输出异常类型，不打印消息、堆栈或密码。
            System.err.println("冷副本核验拒绝或失败：" + exception.getClass().getSimpleName());
            System.exit(2);
        }
    }

    private static void inspect(String directoryArgument, String credentialArgument) throws Exception {
        Path copy = canonicalExisting(directoryArgument);
        if (copy.equals(Path.of("/var/lib/vector-search/db"))
                || copy.startsWith(Path.of("/var/lib/vector-search/db"))) {
            throw new IllegalArgumentException("禁止生产数据库路径");
        }
        String location = copy.toString();
        if (!location.matches("/var/lib/vector-search/(restore-check-|cold-snapshot-)[A-Za-z0-9._-]+")
                && !location.matches("(/private)?/tmp/12times-cold-snapshot-[A-Za-z0-9._-]+/copy[A-Za-z0-9._-]*")) {
            throw new IllegalArgumentException("仅接受专用全新副本目录");
        }
        if (!Files.getPosixFilePermissions(copy).equals(DIRECTORY_READ_ONLY)) {
            throw new IllegalArgumentException("副本目录必须已封存为0500");
        }
        Path database = copy.resolve("knowledge.mv.db");
        Path marker = copy.resolve("cold-snapshot.properties");
        requireReadOnlyFile(database);
        requireReadOnlyFile(marker);
        try (var entries = Files.list(copy)) {
            if (entries.anyMatch(p -> !p.equals(database) && !p.equals(marker))) {
                throw new IllegalArgumentException("拒绝活动锁、附件或非副本文件");
            }
        }
        Properties metadata = new Properties();
        try (InputStream stream = Files.newInputStream(marker)) {
            metadata.load(stream);
        }
        if (!"vector-search-cold-snapshot-v1".equals(metadata.getProperty("purpose"))
                || !SOURCE_SHA.equals(metadata.getProperty("sourceSha"))
                || !JAR_SHA.equals(metadata.getProperty("jarSha256"))
                || !PREFIX.equals(metadata.getProperty("syntheticPrefix"))
                || !copy.toString().equals(metadata.getProperty("copyDirectory"))) {
            throw new IllegalArgumentException("副本标识不符");
        }
        String before = sha256(database);
        if (!before.equals(metadata.getProperty("databaseSha256"))) {
            throw new IllegalArgumentException("副本数据库摘要不符");
        }
        // 单文件搜索有任何匹配都拒绝；警告/不可识别结果不当作无占用。
        Process lsof = new ProcessBuilder("lsof", "-nP", "-t", "--", database.toString()).start();
        if (!lsof.waitFor(10, TimeUnit.SECONDS)) {
            lsof.destroy();
            throw new IllegalStateException("占用检查超时");
        }
        if (lsof.exitValue() != 1 || lsof.getInputStream().readAllBytes().length != 0
                || lsof.getErrorStream().readAllBytes().length != 0) {
            throw new IllegalStateException("副本被占用或无法可靠检查");
        }
        Path credentials = canonicalExisting(credentialArgument);
        Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(credentials);
        if (!Files.isRegularFile(credentials) || permissions.contains(PosixFilePermission.GROUP_WRITE)
                || permissions.stream().anyMatch(p -> p.name().startsWith("OTHERS_"))) {
            throw new IllegalArgumentException("凭据文件权限不受限");
        }
        String password = null;
        for (String line : Files.readAllLines(credentials, StandardCharsets.UTF_8)) {
            if (line.startsWith("DB_PASSWORD=")) {
                if (password != null) {
                    throw new IllegalArgumentException("凭据重复");
                }
                password = line.substring("DB_PASSWORD=".length());
            }
        }
        if (password == null || !password.matches("[A-Za-z0-9_-]{1,256}")) {
            throw new IllegalArgumentException("仅支持本项目安全生成的非空凭据格式");
        }
        // 在打开文件前核对批准JAR内的H2版本，不自动换驱动或降级到内存库。
        Class<?> constants = Class.forName("org.h2.engine.Constants");
        Object version = constants.getField("VERSION").get(null);
        Path driver = Path.of(constants.getProtectionDomain().getCodeSource().getLocation().toURI());
        if (!"2.4.240".equals(version) || !Files.isRegularFile(driver) || !H2_SHA.equals(sha256(driver))) {
            throw new IllegalStateException("H2版本不匹配");
        }
        String url = "jdbc:h2:file:" + copy.resolve("knowledge")
                + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;DB_CLOSE_ON_EXIT=FALSE";
        String sql = "SELECT d.document_id,d.raw_text,d.channel,d.submitted_at AS doc_submitted_at,"
                + "d.invalidated,d.invalidated_at,d.vector_bytes,d.embedding_version AS doc_version,"
                + "d.vector_completed_at,d.hit_count,t.task_id,t.status,t.run_token,t.attempt_count,"
                + "t.embedding_version AS task_version,t.submitted_at AS task_submitted_at,"
                + "t.started_at,t.finished_at,t.error_code,t.error_message "
                + "FROM documents d LEFT JOIN embedding_tasks t ON t.document_id=d.document_id "
                + "WHERE d.document_id LIKE ? ORDER BY d.document_id,t.task_id";
        List<String> records = new ArrayList<>();
        try (Connection connection = DriverManager.getConnection(url, "sa", password)) {
            connection.setReadOnly(true);
            try (PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setString(1, PREFIX + "%");
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        records.add(record(result));
                    }
                }
            }
        }
        if (records.isEmpty() || !before.equals(sha256(database))) {
            throw new IllegalStateException("缺少合成行或副本发生变化");
        }
        // 关闭连接且复核原副本摘要之后才发布快照，失败不输出半份成功结果。
        for (String record : records) {
            System.out.println(record);
        }
        System.err.println("只读冷副本核验成功，合成行数=" + records.size() + "，完整文件摘要未变");
    }

    private static String record(ResultSet result) throws Exception {
        byte[] vector = result.getBytes("vector_bytes");
        if (vector != null && vector.length != 1024) {
            throw new IllegalStateException("向量长度异常");
        }
        List<String> fields = new ArrayList<>();
        for (String column : List.of("document_id", "channel", "doc_version", "task_id", "status", "run_token", "task_version", "error_code")) {
            fields.add(quote(column) + ":" + quote(result.getString(column)));
        }
        fields.add("\"text_sha256\":" + quote(sha256(result.getString("raw_text").getBytes(StandardCharsets.UTF_8))));
        fields.add("\"vector_length\":" + (vector == null ? "null" : vector.length));
        fields.add("\"vector_sha256\":" + quote(vector == null ? null : sha256(vector)));
        fields.add("\"hit_count\":" + quote(result.getString("hit_count")));
        fields.add("\"invalidated\":" + result.getBoolean("invalidated"));
        fields.add("\"attempt_count\":" + quote(result.getString("attempt_count")));
        String error = result.getString("error_message");
        fields.add("\"error_message_sha256\":" + quote(error == null ? null : sha256(error.getBytes(StandardCharsets.UTF_8))));
        for (String column : List.of("doc_submitted_at", "invalidated_at", "vector_completed_at", "task_submitted_at", "started_at", "finished_at")) {
            OffsetDateTime time = result.getObject(column, OffsetDateTime.class);
            fields.add(quote(column) + ":" + quote(time == null ? null : time.toInstant().toString()));
        }
        return "{" + String.join(",", fields) + "}";
    }

    private static Path canonicalExisting(String argument) throws Exception {
        Path path = Path.of(argument).toAbsolutePath().normalize();
        if (!path.equals(path.toRealPath()) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("路径必须规范且无链接");
        }
        return path;
    }

    private static void requireReadOnlyFile(Path file) throws Exception {
        if (Files.isSymbolicLink(file) || !Files.isRegularFile(file)
                || !Files.getPosixFilePermissions(file).equals(FILE_READ_ONLY)) {
            throw new IllegalArgumentException("副本文件必须为0400普通文件");
        }
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream stream = Files.newInputStream(path)) {
            byte[] buffer = new byte[1024 * 1024];
            for (int read; (read = stream.read(buffer)) != -1;) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String quote(String text) {
        if (text == null) {
            return "null";
        }
        StringBuilder escaped = new StringBuilder("\"");
        for (char character : text.toCharArray()) {
            if (character == '"' || character == '\\') {
                escaped.append('\\').append(character);
            } else if (character < 32) {
                escaped.append(String.format("\\u%04x", (int) character));
            } else {
                escaped.append(character);
            }
        }
        return escaped.append('"').toString();
    }
}
