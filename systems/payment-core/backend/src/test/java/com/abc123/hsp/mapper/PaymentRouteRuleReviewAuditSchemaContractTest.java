package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import org.junit.jupiter.api.Test;

/**
 * 路由规则复核冲突审计字段在全量 schema、迁移与 Mapper 中的静态契约。
 */
class PaymentRouteRuleReviewAuditSchemaContractTest {

    @Test
    void shouldKeepRouteRuleReviewAuditSchemaMigrationAndMapperAligned() throws Exception {
        String backendSchema = read("src/main/resources/schema.sql");
        String databaseSchema = read("../database/schema.sql");
        String migration = read(
                "../database/migrations/2026-09-13-add-payment-route-rule-review-conflict-audit.sql");
        String mapper = read("src/main/resources/mapper/PaymentConfigMapper.xml");

        assertCurrentSchema(backendSchema);
        assertCurrentSchema(databaseSchema);
        assertTrue(databaseSchema.contains("CREATE TABLE t_payment_route_rule_version"));
        assertTrue(databaseSchema.contains("CREATE TABLE t_payment_route_rule_review"));
        assertTrue(migration.contains("ALTER TABLE t_payment_route_rule_review"));
        assertTrue(migration.contains("ADD COLUMN priority_collision_detected TINYINT(1) NULL"));
        assertTrue(migration.contains("ADD COLUMN priority_collision_acknowledged TINYINT(1) NULL"));
        assertTrue(mapper.contains("priority_collision_detected AS priorityCollisionDetected"));
        assertTrue(mapper.contains("priority_collision_acknowledged AS priorityCollisionAcknowledged"));
        assertTrue(mapper.contains("priority_collision_detected = #{priorityCollisionDetected}"));
        assertTrue(mapper.contains("priority_collision_acknowledged = #{priorityCollisionAcknowledged}"));
    }

    private void assertCurrentSchema(String schema) {
        assertTrue(schema.contains(
                "priority_collision_detected TINYINT(1) DEFAULT NULL COMMENT '批准复验是否检测到同优先级潜在冲突，未执行批准复验为空'"));
        assertTrue(schema.contains(
                "priority_collision_acknowledged TINYINT(1) DEFAULT NULL COMMENT '检测到潜在冲突时是否完成显式风险确认，未执行批准复验为空'"));
    }

    private String read(String path) throws Exception {
        return new String(Files.readAllBytes(
                Paths.get(System.getProperty("basedir"), path).normalize()), StandardCharsets.UTF_8);
    }
}
