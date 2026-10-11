package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.Test;

class PaymentPrepaySourceAuditSqlTest {

    @Test
    void shouldInventoryUnknownAndDriftedSourcesWithoutBackfillingFromCurrentOrders() throws Exception {
        String auditSql = new String(Files.readAllBytes(Paths.get(
                "../database/audits/2026-10-05-prepay-source-kind-readonly.sql")),
                StandardCharsets.UTF_8);
        String[] statements = auditSql.replaceAll("(?m)^--.*(?:\\R|$)", "").trim().split(";");
        assertEquals(5, statements.length);
        assertEquals("USE housekeeping_payment_core", statements[0].trim());
        for (int statementIndex = 1; statementIndex < statements.length; statementIndex++) {
            assertTrue(statements[statementIndex].trim().startsWith("SELECT"));
        }
        assertFalse(auditSql.contains("UPDATE t_prepay_order"));

        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:prepay_source_audit;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_payment_order (payment_order_id VARCHAR(64) PRIMARY KEY, "
                    + "status VARCHAR(32), amount DECIMAL(18,2), success_at DATETIME)");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64) PRIMARY KEY)");
            statement.execute("CREATE TABLE t_prepay_order (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "prepay_order_no VARCHAR(64), payment_order_id VARCHAR(64), order_no VARCHAR(64), "
                    + "amount DECIMAL(18,2), source_kind VARCHAR_IGNORECASE(16), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES "
                    + "('PAY-A', 'WAIT_CALLBACK', 10.00, NULL), "
                    + "('PAY-B', 'SUCCESS', 20.00, '2026-10-05 10:00:00'), "
                    + "('PAY-C', 'SUCCESS', 30.00, '2026-10-05 10:00:00'), "
                    + "('PAY-E', 'WAIT_CALLBACK', 50.00, NULL), "
                    + "('PAY-F', 'SUCCESS', 60.00, '2026-10-05 10:00:00'), "
                    + "('PAY-G', 'WAIT_CALLBACK', 70.00, NULL), "
                    + "('PAY-H', 'WAIT_CALLBACK', 80.00, NULL)");
            statement.executeUpdate("INSERT INTO t_order VALUES ('ORD-A'), ('ORD-F'), ('ORD-G')");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, amount, source_kind, created_at) VALUES "
                    + "('PRE-A', 'PAY-A', 'ORD-A', 10.00, 'UNKNOWN', '2026-10-05 09:00:00'), "
                    + "('PRE-B', 'PAY-B', 'ORD-B', 20.00, 'UNKNOWN', '2026-10-05 09:01:00'), "
                    + "('PRE-C', 'PAY-C', 'ORD-C', 30.00, 'VIRTUAL', '2026-10-05 09:02:00'), "
                    + "('PRE-D', NULL, 'ORD-D', 40.00, 'UNKNOWN', '2026-10-05 09:03:00'), "
                    + "('PRE-E', 'PAY-E', 'ORD-E', 50.00, 'STANDARD_ORDER', '2026-10-05 09:04:00'), "
                    + "('PRE-F', 'PAY-F', 'ORD-F', 60.00, 'VIRTUAL', '2026-10-05 09:05:00'), "
                    + "('PRE-G', 'PAY-G', 'ORD-G', 70.00, 'STANDARD_ORDER', '2026-10-05 09:06:00'), "
                    + "('PRE-H', 'PAY-H', 'ORD-H', 80.00, 'LEGACY', '2026-10-05 09:07:00')");

            try (ResultSet summary = statement.executeQuery(statements[1])) {
                assertTrue(summary.next());
                assertEquals("MISSING_PAYMENT", summary.getString("payment_status"));
                assertEquals("ABSENT_NOW", summary.getString("current_order_presence"));
                assertEquals(1, summary.getInt("unknown_prepay_count"));
                assertTrue(summary.next());
                assertEquals("SUCCESS", summary.getString("payment_status"));
                assertEquals(1, summary.getInt("unknown_prepay_count"));
                assertTrue(summary.next());
                assertEquals("WAIT_CALLBACK", summary.getString("payment_status"));
                assertEquals("PRESENT_NOW", summary.getString("current_order_presence"));
                assertEquals(1, summary.getInt("unknown_prepay_count"));
                assertFalse(summary.next());
            }
            try (ResultSet details = statement.executeQuery(statements[2])) {
                assertTrue(details.next());
                assertEquals("PRE-A", details.getString("prepay_order_no"));
                assertEquals("PRESENT_NOW", details.getString("current_order_presence"));
                assertEquals(1, details.getInt("payment_prepay_count"));
                assertTrue(details.next());
                assertEquals("PRE-B", details.getString("prepay_order_no"));
                assertEquals("ABSENT_NOW", details.getString("current_order_presence"));
                assertTrue(details.next());
                assertEquals("PRE-D", details.getString("prepay_order_no"));
                assertEquals("MISSING_PAYMENT", details.getString("payment_status"));
                assertFalse(details.next());
            }
            try (ResultSet summary = statement.executeQuery(statements[3])) {
                assertTrue(summary.next());
                assertEquals("SUCCESS", summary.getString("payment_status"));
                assertEquals("VIRTUAL_COLLISION_NOW", summary.getString("conflict_type"));
                assertEquals(1, summary.getInt("conflicted_prepay_count"));
                assertTrue(summary.next());
                assertEquals("WAIT_CALLBACK", summary.getString("payment_status"));
                assertEquals("INVALID_SOURCE_KIND", summary.getString("conflict_type"));
                assertEquals(1, summary.getInt("conflicted_prepay_count"));
                assertTrue(summary.next());
                assertEquals("WAIT_CALLBACK", summary.getString("payment_status"));
                assertEquals("STANDARD_MISSING_NOW", summary.getString("conflict_type"));
                assertEquals(1, summary.getInt("conflicted_prepay_count"));
                assertFalse(summary.next());
            }
            try (ResultSet details = statement.executeQuery(statements[4])) {
                assertTrue(details.next());
                assertEquals("PRE-H", details.getString("prepay_order_no"));
                assertEquals("INVALID_SOURCE_KIND", details.getString("conflict_type"));
                assertTrue(details.next());
                assertEquals("PRE-E", details.getString("prepay_order_no"));
                assertEquals("STANDARD_MISSING_NOW", details.getString("conflict_type"));
                assertTrue(details.next());
                assertEquals("PRE-F", details.getString("prepay_order_no"));
                assertEquals("VIRTUAL_COLLISION_NOW", details.getString("conflict_type"));
                assertFalse(details.next());
            }
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='virtual' "
                    + "WHERE prepay_order_no='PRE-C'");
            try (ResultSet summary = statement.executeQuery(statements[3])) {
                assertTrue(summary.next());
                assertEquals("SUCCESS", summary.getString("payment_status"));
                assertEquals("INVALID_SOURCE_KIND", summary.getString("conflict_type"));
                assertEquals(1, summary.getInt("conflicted_prepay_count"));
            }
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0)) "
                    + "WHERE prepay_order_no='PRE-C'");
            try (ResultSet details = statement.executeQuery(statements[4])) {
                assertTrue(details.next());
                assertEquals("PRE-H", details.getString("prepay_order_no"));
                assertTrue(details.next());
                assertEquals("PRE-E", details.getString("prepay_order_no"));
                assertTrue(details.next());
                assertEquals("PRE-F", details.getString("prepay_order_no"));
                assertTrue(details.next());
                assertEquals("PRE-C", details.getString("prepay_order_no"));
                assertEquals("INVALID_SOURCE_KIND", details.getString("conflict_type"));
                assertFalse(details.next());
            }
        }
    }
}
