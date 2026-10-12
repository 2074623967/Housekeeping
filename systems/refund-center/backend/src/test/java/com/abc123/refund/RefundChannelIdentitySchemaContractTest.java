package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class RefundChannelIdentitySchemaContractTest {

    @Test
    void shouldBindRefundChannelIdentityAtomicallyAcrossSchemaBaselines() throws Exception {
        String h2Schema = read("src/main/resources/schema.sql");
        String mysqlSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-12-add-refund-channel-identity.sql");
        String mapper = read("src/main/resources/mapper/RefundMapper.xml");

        for (String schema : new String[] { h2Schema, mysqlSchema }) {
            int refundOrder = schema.indexOf("CREATE TABLE IF NOT EXISTS t_refund_order (");
            if (refundOrder < 0) {
                refundOrder = schema.indexOf("CREATE TABLE t_refund_order (");
            }
            assertTrue(refundOrder >= 0);
            int tableEnd = schema.indexOf(") ENGINE", refundOrder);
            if (tableEnd < 0) {
                tableEnd = schema.indexOf(");", refundOrder);
            }
            assertTrue(tableEnd > refundOrder);
            String orderDefinition = schema.substring(refundOrder, tableEnd);
            assertTrue(orderDefinition.contains("channel_code VARCHAR(64)"));
            assertTrue(orderDefinition.contains("uk_refund_channel_identity"));
            assertTrue(orderDefinition.contains("(channel_code, channel_refund_id)"));
        }
        assertTrue(migration.contains("ADD COLUMN channel_code VARCHAR(64) NULL"));
        assertTrue(migration.contains("ADD UNIQUE KEY uk_refund_channel_identity "
                + "(channel_code, channel_refund_id)"));
        assertFalse(migration.contains("UPDATE t_refund_order"));
        assertTrue(mapper.contains("#{paymentOrderId}, #{channelCode}, #{orderNo}"));
        assertTrue(mapper.contains("NULLIF(TRIM(channel_code), '') IS NOT NULL"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(System.getProperty("basedir"), path).normalize()),
                StandardCharsets.UTF_8);
    }
}
