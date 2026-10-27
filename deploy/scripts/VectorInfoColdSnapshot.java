import java.io.BufferedWriter;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 在隔离且封存的冷副本上导出两张业务表的全部列，绝不连接生产数据库。
 * 参数为副本主文件、独立核实的文件SHA-256、新TSV文件；密码仅从受限进程环境
 * VECTOR_SNAPSHOT_DB_PASSWORD读取。运行时由发布Owner提供固定H2 2.5.252驱动。
 * TSV包含敏感业务原文的Base64，必须留在受限本地，不能公开；stdout仅给数量和文件摘要。
 */
public final class VectorInfoColdSnapshot {
    private static final Path PRODUCTION = Path.of("/var/lib/vector-search/db");

    private VectorInfoColdSnapshot() { }

    /** 不接受JDBC URL、SQL或密码参数，异常消息也不输出，避免驱动回显秘密。 */
    public static void main(String[] args) {
        try {
            if (args.length != 3) {
                throw new IllegalArgumentException();
            }
            inspect(args[0], args[1], args[2]);
        } catch (Exception exception) {
            System.err.println("冷副本核证失败：" + exception.getClass().getSimpleName());
            System.exit(2);
        }
    }

    private static void inspect(String databaseName, String approvedSha, String outputName) throws Exception {
        // Linux必须以root保证lsof对其他账号进程的可见性；macOS仅用于同账号夹具验证。
        if (System.getProperty("os.name").equals("Linux")
                && ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).longValue() != 0) {
            throw new IllegalStateException();
        }
        Path database = checkedPath(databaseName);
        Path directory = database.getParent();
        String location = directory.toString();
        // 固定隔离根排除任意改名的运行目录；临时根只用于Owner自建的本地夹具。
        if (database.startsWith(PRODUCTION) || !database.getFileName().toString().equals("knowledge.mv.db")
                || !location.matches("(/var/lib/vector-search|/private/tmp|/tmp)/(cold-snapshot-|restore-check-)[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException();
        }
        requireMode(directory, "r-x------");
        requireMode(database, "r--------");
        if (!Files.isRegularFile(database, LinkOption.NOFOLLOW_LINKS)
                || ((Number) Files.getAttribute(database, "unix:nlink")).longValue() != 1) {
            throw new IllegalArgumentException();
        }
        // 运行锁、trace或其他附件均需要Owner另行判断，工具不能自行忽略或删除。
        try (var entries = Files.list(directory)) {
            if (entries.anyMatch(path -> !path.equals(database))) {
                throw new IllegalArgumentException();
            }
        }
        if (!approvedSha.matches("[0-9a-f]{64}") || !approvedSha.equals(sha256(database))) {
            throw new IllegalArgumentException();
        }
        ensureUnused(database);
        Path output = checkedPath(outputName);
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) || output.startsWith(directory)
                || output.startsWith(PRODUCTION)) {
            throw new IllegalArgumentException();
        }
        requireMode(output.getParent(), "rwx------");
        String password = System.getenv("VECTOR_SNAPSHOT_DB_PASSWORD");
        if (password == null || password.isEmpty() || password.length() > 1024) {
            throw new IllegalArgumentException();
        }
        String stem = database.toString().substring(0, database.toString().length() - ".mv.db".length());
        String url = "jdbc:h2:file:" + stem + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r;DB_CLOSE_ON_EXIT=FALSE";
        if (!"2.5.252".equals(Class.forName("org.h2.engine.Constants").getField("VERSION").get(null))) {
            throw new IllegalStateException();
        }
        long documents;
        long tasks;
        // 文件只能CREATE_NEW；失败保留无COMPLETE尾标的现场，不删除或覆盖用户材料。
        try (Connection connection = DriverManager.getConnection(url, "sa", password)) {
            if (!connection.getMetaData().getDatabaseProductName().equals("H2")) {
                throw new IllegalStateException();
            }
            connection.setReadOnly(true);
            connection.setAutoCommit(false);
            Files.createFile(output, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.WRITE)) {
                writer.write("VECTOR_INFO_COLD_SNAPSHOT_V1\t" + approvedSha + "\n");
                writer.write("H2_VERSION\t" + base64(connection.getMetaData().getDatabaseProductVersion()) + "\n");
                writer.write("table\trow\tcolumn\tjdbc_type\tencoding\tvalue\n");
                documents = export(connection, writer, "documents", "document_id");
                tasks = export(connection, writer, "embedding_tasks", "task_id");
            }
            connection.rollback();
        }
        // H2关闭后再次核整个主文件；不能用只读标志替代真实字节不变核验。
        if (!approvedSha.equals(sha256(database))) {
            throw new IllegalStateException();
        }
        try (BufferedWriter writer = Files.newBufferedWriter(output, StandardCharsets.UTF_8, StandardOpenOption.APPEND)) {
            writer.write("COMPLETE\t" + documents + "\t" + tasks + "\t" + approvedSha + "\n");
        }
        System.out.println("documents=" + documents + " embedding_tasks=" + tasks + " output_sha256=" + sha256(output));
    }

    /** 固定表名和主键来自代码，不接收外部SQL；逐字段编码保持null、空串、换行和大整数区别。 */
    private static long export(Connection connection, BufferedWriter writer, String table, String key) throws Exception {
        long rows = 0;
        try (var statement = connection.prepareStatement("SELECT * FROM " + table + " ORDER BY " + key);
                ResultSet result = statement.executeQuery()) {
            ResultSetMetaData metadata = result.getMetaData();
            while (result.next()) {
                rows++;
                for (int index = 1; index <= metadata.getColumnCount(); index++) {
                    String name = metadata.getColumnName(index).toLowerCase(java.util.Locale.ROOT);
                    int type = metadata.getColumnType(index);
                    String encoding = "base64-utf8";
                    String value;
                    if (type == Types.BINARY || type == Types.VARBINARY || type == Types.LONGVARBINARY) {
                        byte[] bytes = result.getBytes(index);
                        encoding = bytes == null ? "null" : "bytes-length-sha256";
                        value = bytes == null ? "" : bytes.length + ":" + digest(bytes);
                    } else if (type == Types.TIMESTAMP_WITH_TIMEZONE) {
                        OffsetDateTime timestamp = result.getObject(index, OffsetDateTime.class);
                        encoding = timestamp == null ? "null" : encoding;
                        value = timestamp == null ? "" : base64(timestamp.withOffsetSameInstant(ZoneOffset.UTC).toString());
                    } else if (Set.of(Types.VARCHAR, Types.CHAR, Types.CLOB, Types.BOOLEAN,
                            Types.BIGINT, Types.INTEGER, Types.SMALLINT, Types.TINYINT).contains(type)) {
                        String text = result.getString(index);
                        encoding = text == null ? "null" : encoding;
                        value = text == null ? "" : base64(text);
                    } else {
                        // 新列类型必须明确编码，不能用驱动对象toString伪造稳定快照。
                        throw new IllegalStateException();
                    }
                    writer.write(table + "\t" + rows + "\t" + name + "\t" + type + "\t" + encoding + "\t" + value + "\n");
                }
            }
        }
        return rows;
    }

    /** 所有已有路径分量拒绝链接；分号等JDBC元字符拒绝，避免路径成为连接参数。 */
    private static Path checkedPath(String name) throws Exception {
        Path path = Path.of(name);
        if (!path.isAbsolute() || !path.equals(path.normalize()) || name.indexOf(';') >= 0
                || name.indexOf('\n') >= 0 || name.indexOf('\r') >= 0 || name.indexOf('\\') >= 0) {
            throw new IllegalArgumentException();
        }
        for (Path part = path; part != null; part = part.getParent()) {
            if (Files.isSymbolicLink(part)) {
                throw new IllegalArgumentException();
            }
        }
        return path;
    }

    private static void requireMode(Path path, String mode) throws Exception {
        if (!Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS)
                .equals(PosixFilePermissions.fromString(mode))) {
            throw new IllegalArgumentException();
        }
    }

    /** lsof任何占用、警告或超时都拒绝；无输出且退出1才可视为未占用。 */
    private static void ensureUnused(Path database) throws Exception {
        Process process = new ProcessBuilder("lsof", "-nP", "-t", "--", database.toString()).start();
        if (!process.waitFor(10, TimeUnit.SECONDS)) {
            process.destroy();
            throw new IllegalStateException();
        }
        if (process.exitValue() != 1 || process.getInputStream().readAllBytes().length != 0
                || process.getErrorStream().readAllBytes().length != 0) {
            throw new IllegalStateException();
        }
    }

    private static String base64(String value) {
        return Base64.getEncoder().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String digest(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private static String sha256(Path path) throws Exception {
        MessageDigest hash = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int count;
            while ((count = input.read(buffer)) != -1) {
                hash.update(buffer, 0, count);
            }
        }
        return HexFormat.of().formatHex(hash.digest());
    }
}
