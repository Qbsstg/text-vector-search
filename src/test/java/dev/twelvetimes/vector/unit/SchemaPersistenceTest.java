package dev.twelvetimes.vector.unit;

import java.nio.file.Path;
import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaPersistenceTest {
    @TempDir Path directory;

    @Test
    void schemaCanRunTwiceAndRetainsDocumentAndTaskAfterClosingFileDatabase() throws Exception {
        String url = "jdbc:h2:file:" + directory.resolve("knowledge") + ";WRITE_DELAY=0";
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var statement = connection.createStatement()) {
                statement.executeUpdate("INSERT INTO documents(document_id, raw_text, channel, submitted_at) "
                        + "VALUES ('C0-001', '', 'default', CURRENT_TIMESTAMP)");
                statement.executeUpdate("INSERT INTO embedding_tasks(task_id, document_id, status, embedding_version, submitted_at) "
                        + "VALUES ('00000000-0000-0000-0000-000000000001', 'C0-001', 'QUEUED', 'c0-test', CURRENT_TIMESTAMP)");
            }
        }
        try (var connection = DriverManager.getConnection(url, "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var statement = connection.createStatement();
                 var result = statement.executeQuery("SELECT d.raw_text, t.status, d.hit_count FROM documents d "
                         + "JOIN embedding_tasks t ON d.document_id=t.document_id WHERE d.document_id='C0-001'")) {
                result.next();
                assertEquals("", result.getString(1));
                assertEquals("QUEUED", result.getString(2));
                assertEquals(0L, result.getLong(3));
            }
        }
    }

    @Test
    void partialVectorAndDanglingTaskAreRejected() throws Exception {
        try (var connection = DriverManager.getConnection("jdbc:h2:mem:constraints", "sa", "")) {
            ScriptUtils.executeSqlScript(connection, new ClassPathResource("schema.sql"));
            try (var statement = connection.createStatement()) {
                assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(
                        "INSERT INTO embedding_tasks(task_id,document_id,status,embedding_version,submitted_at) "
                                + "VALUES ('missing','missing','QUEUED','test',CURRENT_TIMESTAMP)"));
                statement.executeUpdate("INSERT INTO documents(document_id,raw_text,channel,submitted_at) "
                        + "VALUES ('length-check','','default',CURRENT_TIMESTAMP)");
            }
            try (var statement = connection.prepareStatement("UPDATE documents SET vector_bytes=?, "
                    + "embedding_version='test', vector_completed_at=CURRENT_TIMESTAMP WHERE document_id='length-check'")) {
                statement.setBytes(1, new byte[1023]);
                assertThrows(java.sql.SQLException.class, statement::executeUpdate);
            }
        }
    }
}

