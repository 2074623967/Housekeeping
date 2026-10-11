package com.abc123.riskcontrol.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RiskControlIdentityMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:risk_control_identity_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "");
        Configuration configuration = new Configuration(new Environment(
                "test", new JdbcTransactionFactory(), dataSource));
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(RiskControlMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RiskControlMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RiskControlMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE TABLE t_risk_review_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, review_no VARCHAR_IGNORECASE(64) NOT NULL UNIQUE, "
                    + "business_no VARCHAR_IGNORECASE(64) NOT NULL, business_key BINARY(32) UNIQUE, "
                    + "risk_tag VARCHAR(128), "
                    + "request_fingerprint VARCHAR(64), "
                    + "review_item VARCHAR(255), status VARCHAR(32), status_type VARCHAR(32), "
                    + "reviewer VARCHAR(64), updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_risk_intercept_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64) NOT NULL UNIQUE, "
                    + "event_key BINARY(32) UNIQUE, "
                    + "payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, hit_policy VARCHAR_IGNORECASE(64), "
                    + "risk_level VARCHAR(64), decision_result VARCHAR(64), "
                    + "source_system VARCHAR(64), happened_at DATETIME NOT NULL)");
            statement.execute("INSERT INTO t_risk_review_order (review_no, business_no, risk_tag, "
                    + "review_item, status, status_type, updated_at) VALUES "
                    + "('REVIEW-Exact-001', 'PAY-Exact-001', '人工复核', '核实支付', "
                    + "'APPROVED', 'success', NOW()), "
                    + "('REVIEW-Exact-002', 'PAY-Exact-002', '人工复核', '核实支付', "
                    + "'PENDING', 'warn', NOW()), "
                    + "('REVIEW-Exact-003', 'PAY-Exact-003', '人工复核', '核实支付', "
                    + "'pending', 'warn', NOW())");
            statement.execute("INSERT INTO t_risk_intercept_event (event_no, payment_order_id, "
                    + "hit_policy, risk_level, decision_result, source_system, happened_at) VALUES "
                    + "('RISK-EVT-Exact-001', 'PAY-Exact-001', 'POLICY-Exact', '高', "
                    + "'已拦截', 'payment-core', NOW())");
            session.commit();
        }
    }

    @Test
    void shouldNotReuseApprovalOrApplyActionThroughCaseVariantIdentifiers() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RiskControlMapper mapper = session.getMapper(RiskControlMapper.class);
            assertNull(mapper.findLatestReviewOrderByBusinessNo("pay-exact-001"));
            ReviewOrderEntity approved = mapper.findLatestReviewOrderByBusinessNo("PAY-Exact-001");
            assertEquals("REVIEW-Exact-001", approved.getReviewNo());
            assertNull(mapper.findReviewOrderByReviewNo("review-exact-002"));
            assertEquals(0, mapper.updateReviewOrderWhenPending(
                    "review-exact-002", "APPROVED", "success", "审核员"));
            session.commit();
            assertEquals("PENDING", mapper.findReviewOrderByReviewNo("REVIEW-Exact-002").getStatus());
            assertEquals(1, mapper.updateReviewOrderWhenPending(
                    "REVIEW-Exact-002", "APPROVED", "success", "审核员"));
            assertEquals(0, mapper.updateReviewOrderWhenPending(
                    "REVIEW-Exact-003", "APPROVED", "success", "审核员"));
            session.commit();
            assertEquals("APPROVED", mapper.findReviewOrderByReviewNo("REVIEW-Exact-002").getStatus());
            assertEquals("pending", mapper.findReviewOrderByReviewNo("REVIEW-Exact-003").getStatus());
        }
    }

    @Test
    void shouldNotReuseInterceptEventThroughCaseVariantPaymentOrPolicy() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RiskControlMapper mapper = session.getMapper(RiskControlMapper.class);
            assertNull(mapper.findLatestInterceptEvent("pay-exact-001", "POLICY-Exact", "已拦截", "payment-core"));
            assertNull(mapper.findLatestInterceptEvent("PAY-Exact-001", "policy-exact", "已拦截", "payment-core"));
            assertNull(mapper.findLatestInterceptEvent("PAY-Exact-001", "POLICY-Exact", "已拦截", "Payment-Core"));
            assertNull(mapper.findLatestInterceptEvent("PAY-Exact-001", "POLICY-Exact", "已拦截", "other-system"));
            InterceptEventEntity exact = mapper.findLatestInterceptEvent(
                    "PAY-Exact-001", "POLICY-Exact", "已拦截", "payment-core");
            assertEquals("RISK-EVT-Exact-001", exact.getEventNo());
        }
    }

    @Test
    void shouldLeaveHistoricalReviewContextUnknownAfterMigration() throws Exception {
        String migration = new String(Files.readAllBytes(Paths.get(
                "../database/migrations/2026-10-06-add-risk-review-request-fingerprint.sql")),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:risk_review_migration_" + System.nanoTime() + ";MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_risk_review_order ("
                    + "review_no VARCHAR(64) PRIMARY KEY, business_no VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO t_risk_review_order (review_no, business_no) "
                    + "VALUES ('REVIEW-LEGACY-001', 'PAY-LEGACY-001')");
            statement.execute(migration);
            try (ResultSet migrated = statement.executeQuery(
                    "SELECT request_fingerprint FROM t_risk_review_order "
                            + "WHERE review_no = 'REVIEW-LEGACY-001'")) {
                assertEquals(true, migrated.next());
                assertNull(migrated.getString(1));
            }
        }
    }

    @Test
    void shouldLeaveHistoricalBusinessKeysEmptyAndConstrainNewRowsAfterMigration() throws Exception {
        String migration = new String(Files.readAllBytes(Paths.get(
                "../database/migrations/2026-10-06-add-risk-review-business-key.sql")),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:risk_review_business_key_" + System.nanoTime() + ";MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_risk_review_order ("
                    + "review_no VARCHAR(64) PRIMARY KEY, business_no VARCHAR_IGNORECASE(64) NOT NULL)");
            statement.execute("INSERT INTO t_risk_review_order (review_no, business_no) VALUES "
                    + "('REVIEW-LEGACY-001', 'PAY-LEGACY-001'), "
                    + "('REVIEW-LEGACY-002', 'PAY-LEGACY-001')");
            for (String sql : migration.split(";")) {
                if (!sql.trim().isEmpty()) {
                    statement.execute(sql);
                }
            }
            try (ResultSet historical = statement.executeQuery(
                    "SELECT business_key FROM t_risk_review_order WHERE business_no = 'PAY-LEGACY-001'")) {
                assertEquals(true, historical.next());
                assertNull(historical.getBytes(1));
                assertEquals(true, historical.next());
                assertNull(historical.getBytes(1));
                assertEquals(false, historical.next());
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO t_risk_review_order (review_no, business_no, business_key) VALUES (?, ?, ?)")) {
                byte[] businessKey = MessageDigest.getInstance("SHA-256")
                        .digest("PAY-NEW-001".getBytes(StandardCharsets.UTF_8));
                insert.setString(1, "REVIEW-NEW-001");
                insert.setString(2, "PAY-NEW-001");
                insert.setBytes(3, businessKey);
                assertEquals(1, insert.executeUpdate());
                insert.setString(1, "REVIEW-NEW-002");
                assertThrows(SQLException.class, insert::executeUpdate);
                insert.setString(2, "pay-new-001");
                insert.setBytes(3, MessageDigest.getInstance("SHA-256")
                        .digest("pay-new-001".getBytes(StandardCharsets.UTF_8)));
                assertEquals(1, insert.executeUpdate());
            }
        }
    }

    @Test
    void shouldLeaveHistoricalEventKeysEmptyAndConstrainNewRowsAfterMigration() throws Exception {
        String migration = new String(Files.readAllBytes(Paths.get(
                "../database/migrations/2026-10-06-add-risk-event-key.sql")),
                StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(
                "jdbc:h2:mem:risk_event_key_" + System.nanoTime() + ";MODE=MySQL", "sa", "");
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE t_risk_intercept_event ("
                    + "event_no VARCHAR(64) PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "hit_policy VARCHAR_IGNORECASE(64) NOT NULL, decision_result VARCHAR(64) NOT NULL)");
            statement.execute("INSERT INTO t_risk_intercept_event "
                    + "(event_no, payment_order_id, hit_policy, decision_result) VALUES "
                    + "('RISK-EVT-LEGACY-001', 'PAY-LEGACY-001', 'POLICY-001', '已拦截'), "
                    + "('RISK-EVT-LEGACY-002', 'PAY-LEGACY-001', 'POLICY-001', '已拦截')");
            for (String sql : migration.split(";")) {
                if (!sql.trim().isEmpty()) {
                    statement.execute(sql);
                }
            }
            try (ResultSet historical = statement.executeQuery(
                    "SELECT event_key FROM t_risk_intercept_event WHERE payment_order_id = 'PAY-LEGACY-001'")) {
                assertEquals(true, historical.next());
                assertNull(historical.getBytes(1));
                assertEquals(true, historical.next());
                assertNull(historical.getBytes(1));
                assertEquals(false, historical.next());
            }
            try (PreparedStatement insert = connection.prepareStatement(
                    "INSERT INTO t_risk_intercept_event "
                            + "(event_no, payment_order_id, hit_policy, decision_result, event_key) "
                            + "VALUES (?, ?, 'POLICY-001', '已拦截', ?)")) {
                byte[] eventKey = MessageDigest.getInstance("SHA-256")
                        .digest("PAY-NEW-001|POLICY-001|已拦截".getBytes(StandardCharsets.UTF_8));
                insert.setString(1, "RISK-EVT-NEW-001");
                insert.setString(2, "PAY-NEW-001");
                insert.setBytes(3, eventKey);
                assertEquals(1, insert.executeUpdate());
                insert.setString(1, "RISK-EVT-NEW-002");
                assertThrows(SQLException.class, insert::executeUpdate);
                insert.setString(2, "pay-new-001");
                insert.setBytes(3, MessageDigest.getInstance("SHA-256")
                        .digest("pay-new-001|POLICY-001|已拦截".getBytes(StandardCharsets.UTF_8)));
                assertEquals(1, insert.executeUpdate());
            }
        }
    }
}
