package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 路由规则冲突复验协调锁在 schema、迁移与 Mapper 中的静态契约。
 */
class PaymentRouteRuleConflictLockSchemaContractTest {

    @Test
    void shouldKeepConflictLockSchemaMigrationAndMapperAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-09-13-add-payment-route-rule-conflict-lock.sql");
        String mapper = read("src/main/resources/mapper/PaymentConfigMapper.xml");

        assertCurrentSchema(backendSchema);
        assertCurrentSchema(databaseSchema);
        assertTrue(migration.contains("CREATE TABLE IF NOT EXISTS t_payment_route_rule_conflict_lock"));
        assertTrue(migration.contains("UNIQUE KEY uk_route_rule_conflict_lock (lock_name)"));
        assertTrue(mapper.contains("INSERT IGNORE INTO t_payment_route_rule_conflict_lock"));
        assertTrue(mapper.contains("WHERE lock_name = 'ENABLED_RULE_SET'"));
        assertTrue(mapper.contains("<select id=\"findRouteRulesForConflictCheckForUpdate\""));
        assertTrue(mapper.contains("<include refid=\"routeRuleGovernanceSnapshotColumns\"/>\n        FOR UPDATE"));
    }

    private void assertCurrentSchema(String schema) {
        assertTrue(schema.contains("CREATE TABLE t_payment_route_rule_conflict_lock"));
        assertTrue(schema.contains("lock_name VARCHAR(64) NOT NULL COMMENT '冲突全集协调维度'"));
        assertTrue(schema.contains("UNIQUE KEY uk_route_rule_conflict_lock (lock_name)"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
