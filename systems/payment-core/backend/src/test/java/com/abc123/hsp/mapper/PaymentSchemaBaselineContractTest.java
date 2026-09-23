package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 应用 schema、交付 schema 与增量迁移的支付单基线契约。
 */
class PaymentSchemaBaselineContractTest {

    @Test
    void shouldKeepExpiryRetryTableAndChannelTransactionNullabilityAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-09-23-align-payment-schema-baseline.sql");

        assertSchema(backendSchema);
        assertSchema(databaseSchema);
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_payment_expiry_close_retry"));
        assertTrue(migration.contains(
                "MODIFY COLUMN channel_transaction_no VARCHAR(128) NULL DEFAULT NULL"));
    }

    private void assertSchema(String schema) {
        assertTrue(schema.contains("DROP TABLE IF EXISTS t_payment_expiry_close_retry"));
        assertTrue(schema.contains("CREATE TABLE t_payment_expiry_close_retry"));
        assertTrue(schema.contains("channel_transaction_no VARCHAR(128) DEFAULT NULL"));
        assertTrue(schema.contains("UNIQUE KEY uk_expiry_close_retry_payment (payment_order_id)"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
