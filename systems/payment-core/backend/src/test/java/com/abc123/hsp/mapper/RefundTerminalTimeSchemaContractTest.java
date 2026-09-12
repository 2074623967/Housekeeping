package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 退款终态时间在 schema 与迁移中的静态契约。
 */
class RefundTerminalTimeSchemaContractTest {

    @Test
    void shouldKeepSchemaAndEvidenceBasedMigrationAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-09-12-add-refund-terminal-time.sql");

        assertTerminalTimeSchema(backendSchema);
        assertTerminalTimeSchema(databaseSchema);
        assertTrue(migration.contains("SET terminal_at = success_at"));
        assertTrue(migration.contains("SET refund_order.terminal_at = latest_attempt.completed_at"));
        assertTrue(migration.contains("latest_attempt.attempt_status = refund_order.status"));
        assertTrue(migration.contains("latest_attempt.completed_at IS NOT NULL"));
        assertFalse(migration.contains("SET terminal_at = NOW()"));
        assertFalse(migration.contains("SET terminal_at = applied_at"));
    }

    private void assertTerminalTimeSchema(String schema) {
        assertTrue(schema.contains(
                "terminal_at DATETIME DEFAULT NULL COMMENT '退款终态时间'"));
        assertTrue(schema.contains(
                "KEY idx_refund_status_terminal (status, terminal_at)"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
