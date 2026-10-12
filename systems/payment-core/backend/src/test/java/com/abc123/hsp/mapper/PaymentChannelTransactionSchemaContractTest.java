package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class PaymentChannelTransactionSchemaContractTest {

    @Test
    void shouldKeepPaymentChannelTransactionUniquenessAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-12-add-payment-channel-transaction-unique.sql");

        for (String schema : new String[] {backendSchema, databaseSchema}) {
            assertTrue(schema.contains("channel_transaction_no VARCHAR(128) DEFAULT NULL"));
            assertTrue(schema.contains(
                    "UNIQUE KEY uk_payment_channel_transaction (channel_code, channel_transaction_no)"));
        }
        assertTrue(migration.contains("ALTER TABLE t_payment_order"));
        assertTrue(migration.contains(
                "ADD UNIQUE KEY uk_payment_channel_transaction (channel_code, channel_transaction_no)"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
