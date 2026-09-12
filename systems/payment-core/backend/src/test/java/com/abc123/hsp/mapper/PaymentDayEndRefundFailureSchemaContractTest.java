package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 日终退款失败快照在 schema 与迁移中的静态契约。
 */
class PaymentDayEndRefundFailureSchemaContractTest {

    @Test
    void shouldKeepSchemasAndHistoricalUnknownMigrationAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-09-12-add-day-end-refund-failure.sql");

        assertCurrentSchema(backendSchema);
        assertCurrentSchema(databaseSchema);
        assertTrue(migration.contains("refund_fail_count INT NULL DEFAULT NULL"));
        assertTrue(migration.contains("refund_fail_amount DECIMAL(18, 2) NULL DEFAULT NULL"));
        assertFalse(migration.contains("UPDATE t_payment_day_end_batch"));
    }

    private void assertCurrentSchema(String schema) {
        assertTrue(schema.contains(
                "refund_fail_count INT NOT NULL DEFAULT 0 COMMENT '退款失败单量'"));
        assertTrue(schema.contains(
                "refund_fail_amount DECIMAL(18, 2) NOT NULL DEFAULT 0.00 COMMENT '退款失败金额'"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
