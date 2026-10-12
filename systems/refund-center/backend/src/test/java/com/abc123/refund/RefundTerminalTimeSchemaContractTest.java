package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class RefundTerminalTimeSchemaContractTest {

    @Test
    void shouldKeepTerminalTimeInBothSchemaBaselinesAndMigration() throws Exception {
        String h2Schema = read("src/main/resources/schema.sql");
        String mysqlSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-12-add-refund-terminal-time.sql");
        String mapper = read("src/main/resources/mapper/RefundMapper.xml");

        assertTrue(h2Schema.contains("terminal_at TIMESTAMP NULL"));
        assertTrue(mysqlSchema.contains("terminal_at DATETIME NULL"));
        assertTrue(migration.contains("ADD COLUMN terminal_at DATETIME NULL"));
        assertTrue(mapper.contains("terminal_at = CASE WHEN #{targetStatus} IN ('SUCCESS', 'FAIL')"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
