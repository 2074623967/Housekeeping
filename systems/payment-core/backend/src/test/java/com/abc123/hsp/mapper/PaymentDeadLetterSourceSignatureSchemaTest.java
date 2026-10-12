package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class PaymentDeadLetterSourceSignatureSchemaTest {

    @Test
    void shouldDefaultHistoricalDeadLettersToUnverifiedWhenMigrationRuns() throws Exception {
        String migration = read("../database/migrations/2026-10-13-add-dead-letter-source-signature.sql");
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:dead_letter_source_signature;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_payment_dead_letter_task (id BIGINT PRIMARY KEY, header_snapshot TEXT)");
            statement.execute("INSERT INTO t_payment_dead_letter_task (id) VALUES (1)");
            statement.execute(migration);
            try (ResultSet rows = statement.executeQuery(
                    "SELECT source_signature_verified FROM t_payment_dead_letter_task WHERE id = 1")) {
                assertTrue(rows.next());
                assertFalse(rows.getBoolean(1));
            }
            statement.execute("INSERT INTO t_payment_dead_letter_task (id, source_signature_verified) VALUES (2, 1)");
            try (ResultSet rows = statement.executeQuery(
                    "SELECT source_signature_verified FROM t_payment_dead_letter_task WHERE id = 2")) {
                assertTrue(rows.next());
                assertTrue(rows.getBoolean(1));
            }
        }
        for (String schema : new String[] {
                read("src/main/resources/schema.sql"), read("../database/schema.sql")}) {
            assertTrue(schema.contains("source_signature_verified TINYINT(1) NOT NULL DEFAULT 0"));
        }
        assertTrue(read("src/main/resources/mapper/PaymentDeadLetterTaskMapper.xml")
                .contains("AND source_signature_verified = 1"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
