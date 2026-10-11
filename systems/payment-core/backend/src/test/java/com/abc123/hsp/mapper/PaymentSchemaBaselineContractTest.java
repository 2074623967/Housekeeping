package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
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

    @Test
    void shouldKeepPaymentOrderUpdateTimeAlignedWithRecordQueries() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-10-02-add-payment-order-updated-time.sql");
        String recordMapper = read("src/main/resources/mapper/PaymentRecordMapper.xml");

        assertPaymentOrderUpdateTime(backendSchema);
        assertPaymentOrderUpdateTime(databaseSchema);
        assertTrue(recordMapper.contains("DATE_FORMAT(p.updated_at, '%Y-%m-%d %H:%i:%s') AS updatedAt"));
        assertTrue(migration.contains("ADD COLUMN updated_at DATETIME NULL DEFAULT NULL"));
        assertTrue(migration.contains("SET updated_at = created_at"));
        assertTrue(migration.contains("MODIFY COLUMN updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP"));
        assertTrue(migration.contains("ON UPDATE CURRENT_TIMESTAMP"));
    }

    @Test
    void shouldIndexLatestPrepayLookupByPaymentOrderAndCreationTime() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-10-02-add-payment-prepay-latest-index.sql");
        String index = "idx_prepay_payment_created (payment_order_id, created_at, id)";

        assertTrue(backendSchema.contains("KEY " + index));
        assertTrue(databaseSchema.contains("KEY " + index));
        assertTrue(migration.contains("ALTER TABLE t_prepay_order"));
        assertTrue(migration.contains("ADD INDEX " + index));
    }

    @Test
    void shouldKeepCapturedGatewayNullableAndReadFromLatestAttempt() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-10-02-add-payment-attempt-gateway-code.sql");
        String recordMapper = read("src/main/resources/mapper/PaymentRecordMapper.xml");
        String paymentMapper = read("src/main/resources/mapper/PaymentMapper.xml");

        assertTrue(backendSchema.contains("gateway_code VARCHAR(64) DEFAULT NULL"));
        assertTrue(databaseSchema.contains("gateway_code VARCHAR(64) DEFAULT NULL"));
        assertTrue(migration.contains("ALTER TABLE t_payment_attempt"));
        assertTrue(migration.contains("ADD COLUMN gateway_code VARCHAR(64) NULL DEFAULT NULL"));
        assertTrue(recordMapper.contains("pa.gateway_code AS paymentGateway"));
        assertTrue(paymentMapper.contains(
                "CAST(gateway_code AS BINARY(256)) = CAST(#{gatewayCode} AS BINARY(256))"));
    }

    @Test
    void shouldPersistSubmitRequestFingerprintWithoutInventingHistoricalValues() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-10-10-add-payment-attempt-request-fingerprint.sql");
        String paymentMapper = read("src/main/resources/mapper/PaymentMapper.xml");
        String column = "request_fingerprint VARCHAR(64) DEFAULT NULL";

        assertTrue(backendSchema.contains(column));
        assertTrue(databaseSchema.contains(column));
        assertTrue(migration.contains("ALTER TABLE t_payment_attempt"));
        assertTrue(migration.contains("ADD COLUMN " + column));
        assertFalse(migration.contains("UPDATE t_payment_attempt"));
        assertTrue(paymentMapper.contains("request_fingerprint AS requestFingerprint"));
        assertTrue(paymentMapper.contains("#{requestFingerprint}"));
    }

    @Test
    void shouldKeepExactPaymentAttemptIdempotencyIndexAlignedWithMigration() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-10-10-add-payment-attempt-exact-idempotency.sql");
        String expression = "idempotency_key_bytes VARBINARY(512) "
                + "GENERATED ALWAYS AS (CAST(idempotency_key AS BINARY(512)))";

        for (String schema : new String[] { backendSchema, databaseSchema }) {
            assertTrue(schema.contains(expression));
            assertTrue(schema.contains("UNIQUE KEY uk_attempt_idempotency_bytes (idempotency_key_bytes)"));
            assertTrue(schema.contains("KEY idx_attempt_idempotency (idempotency_key)"));
            assertFalse(schema.contains("UNIQUE KEY uk_attempt_idempotency (idempotency_key)"));
        }
        assertTrue(migration.contains("ADD COLUMN " + expression));
        assertTrue(migration.contains("ADD UNIQUE KEY uk_attempt_idempotency_bytes"));
        assertTrue(migration.contains("DROP INDEX uk_attempt_idempotency"));
        assertTrue(migration.indexOf("ADD UNIQUE KEY uk_attempt_idempotency_bytes")
                < migration.indexOf("DROP INDEX uk_attempt_idempotency"));
    }

    @Test
    void shouldPreserveUnknownHistoricalPrepaySourceUntilVerified() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-05-add-prepay-source-kind.sql");
        String paymentMapper = read("src/main/resources/mapper/PaymentMapper.xml");
        String sourceColumn = "source_kind VARCHAR(16) NOT NULL DEFAULT 'UNKNOWN'";

        assertTrue(backendSchema.contains(sourceColumn));
        assertTrue(databaseSchema.contains(sourceColumn));
        assertTrue(migration.contains("ADD COLUMN " + sourceColumn));
        assertFalse(migration.contains("UPDATE t_prepay_order"));
        assertTrue(paymentMapper.contains("#{sourceKind}"));
        assertTrue(paymentMapper.contains("pr.source_kind AS sourceKind"));
    }

    private void assertPaymentOrderUpdateTime(String schema) {
        int start = schema.indexOf("CREATE TABLE t_payment_order (");
        assertTrue(start >= 0);
        int end = schema.indexOf(") ENGINE=InnoDB", start);
        assertTrue(end > start);
        assertTrue(schema.substring(start, end).contains(
                "updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP"));
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
