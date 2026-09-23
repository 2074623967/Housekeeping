package com.abc123.reconciliation;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * Keeps the runnable schema aligned with the production DDL and fact upsert contract.
 */
class ReconciliationSchemaContractTest {

    @Test
    void shouldKeepFactPayloadTypeAndUpsertContractAligned() throws IOException {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-08-09-add-cross-system-fact-snapshot.sql");
        String mapper = read("src/main/resources/mapper/ReconciliationMapper.xml");

        assertTrue(backendSchema.contains("payload TEXT NULL"));
        assertTrue(databaseSchema.contains("payload TEXT NULL"));
        assertTrue(migration.contains("payload TEXT NULL"));
        assertFalse(backendSchema.contains("payload CLOB"));
        assertTrue(mapper.contains("ON DUPLICATE KEY UPDATE"));
        assertTrue(mapper.contains("payload = VALUES(payload)"));
    }

    private String read(String path) throws IOException {
        return new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8);
    }
}
