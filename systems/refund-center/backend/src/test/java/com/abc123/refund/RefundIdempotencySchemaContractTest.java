package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class RefundIdempotencySchemaContractTest {

    @Test
    void shouldKeepExactIdempotencyIndexAlignedWithMigrationAndLookup() throws Exception {
        String h2Schema = read("src/main/resources/schema.sql");
        String mysqlSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-12-add-exact-refund-idempotency.sql");
        String lookup = read("src/main/resources/mapper/RefundMapper.xml");
        String generatedColumn = "idempotency_key_bytes VARBINARY(512) "
                + "GENERATED ALWAYS AS (CAST(idempotency_key AS BINARY(512)))";

        for (String schema : new String[] { h2Schema, mysqlSchema }) {
            assertTrue(schema.contains(generatedColumn));
            assertTrue(schema.contains("uk_refund_idempotency_bytes"));
            assertTrue(schema.contains("idx_refund_idempotency"));
            assertFalse(schema.contains("uk_refund_idempotency (idempotency_key)"));
        }
        assertTrue(migration.contains("ADD COLUMN " + generatedColumn));
        assertTrue(migration.contains("ADD UNIQUE KEY uk_refund_idempotency_bytes"));
        assertTrue(migration.contains("DROP INDEX uk_refund_idempotency"));
        assertTrue(lookup.contains("CHAR_LENGTH(t_refund_order.idempotency_key) = CHAR_LENGTH(#{idempotencyKey})"));
        assertTrue(lookup.contains("CAST(t_refund_order.idempotency_key AS BINARY(512)) "
                + "= CAST(#{idempotencyKey} AS BINARY(512))"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
