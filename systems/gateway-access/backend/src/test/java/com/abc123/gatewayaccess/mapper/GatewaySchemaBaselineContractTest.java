package com.abc123.gatewayaccess.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

class GatewaySchemaBaselineContractTest {

    @Test
    void shouldIncludeReleaseRouteAndAuditTablesInBothBaselinesAndMigration() throws Exception {
        String h2Schema = read("src/main/resources/schema.sql");
        String mysqlSchema = read("../database/schema.sql");
        String migration = read("../database/migrations/2026-10-12-add-release-route-and-audit.sql");
        String mapper = read("src/main/resources/mapper/GatewayAccessMapper.xml");

        for (String schema : new String[] { h2Schema, mysqlSchema }) {
            assertTrue(schema.contains("CREATE TABLE t_gateway_release_route ("));
            assertTrue(schema.contains("CREATE TABLE t_gateway_audit_log ("));
            assertTrue(schema.contains("UNIQUE KEY uk_release_route_code (route_code)"));
            assertTrue(schema.contains("UNIQUE KEY uk_request_id (request_id)"));
            assertTrue(schema.contains("environment VARCHAR(32) NOT NULL"));
            assertTrue(schema.contains("traffic_percent INT NOT NULL"));
            assertTrue(schema.contains("result_status VARCHAR(32) NOT NULL"));
            assertTrue(schema.contains("risk_hint VARCHAR(255) NOT NULL"));
        }
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_gateway_release_route ("));
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_gateway_audit_log ("));
        assertTrue(migration.contains("UNIQUE KEY uk_release_route_code (route_code)"));
        assertTrue(migration.contains("UNIQUE KEY uk_request_id (request_id)"));
        assertFalse(migration.contains("DROP TABLE"));
        assertFalse(migration.contains("INSERT INTO"));
        assertTrue(mapper.contains("FROM t_gateway_release_route"));
        assertTrue(mapper.contains("INSERT INTO t_gateway_audit_log"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(Paths.get(System.getProperty("basedir"), path).normalize()),
                StandardCharsets.UTF_8);
    }
}
