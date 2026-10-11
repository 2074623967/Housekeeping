package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
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

/**
 * 支付过期关闭与渠道成功收口的原子状态迁移集成测试。
 */
class PaymentMapperStateTransitionIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @Test
    void shouldNotOverwriteSuccessfulPaymentReturnCodeWithStalePendingQuery() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            for (String column : new String[]{"last_channel_return_code VARCHAR(64)",
                    "last_standardized_code VARCHAR(64)", "last_standardized_message VARCHAR(256)",
                    "last_handling_suggestion VARCHAR(256)", "last_retryable BOOLEAN",
                    "last_manual_intervention_required BOOLEAN", "last_mapping_version VARCHAR(64)",
                    "last_mapping_matched BOOLEAN"}) {
                statement.execute("ALTER TABLE t_payment_order ADD COLUMN " + column);
            }
            statement.executeUpdate("UPDATE t_payment_order SET last_channel_return_code='SUCCESS' "
                    + "WHERE payment_order_id='PAY-FINAL-SUCCESS'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updatePaymentReturnCodeSnapshot(
                    "PAY-FINAL-SUCCESS", "USERPAYING", null, null, null, null, null, null, null));
            try (java.sql.ResultSet result = statement.executeQuery(
                    "SELECT last_channel_return_code FROM t_payment_order "
                            + "WHERE payment_order_id='PAY-FINAL-SUCCESS'")) {
                assertTrue(result.next());
                assertEquals("SUCCESS", result.getString(1));
            }
            statement.executeUpdate("UPDATE t_payment_order SET status='success', success_at=NULL "
                    + "WHERE payment_order_id='PAY-FINAL-SUCCESS'");
            assertEquals(0, mapper.updatePaymentReturnCodeSnapshot(
                    "PAY-FINAL-SUCCESS", "USERPAYING", null, null, null, null, null, null, null));
            assertEquals(1, mapper.updatePaymentReturnCodeSnapshot(
                    "PAY-SUBMITTED", "USERPAYING", null, null, null, null, null, null, null));
        }
    }

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_state_transition;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldOnlyCloseExpiredPaymentWithoutChannelSubmissionFacts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.findExpiredPayments(1).size());
            assertEquals("PAY-OLDER", mapper.findExpiredPayments(1).get(0).getPaymentOrderId());
            assertEquals(3, mapper.findExpiredPayments(100).size());
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-LEGACY-BLANK"));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldNotCloseExpiredPaymentWithMultipleExactPrepayBindings() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "prior failure", 60, 2, 3600);
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at, created_at) "
                    + "VALUES ('PAY-OLDER', TIMESTAMPADD(MINUTE, -1, NOW()), NOW())");
            session.clearCache();

            assertFalse(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(0, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldNotCloseExpiredPaymentWhenPrepayAmountOrOrderBindingDrifts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(3, mapper.findExpiredPayments(100).size());
            mapper.recordExpiryCloseFailure("PAY-OLDER", "prior failure", 60, 2, 3600);
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 67.00 "
                    + "WHERE payment_order_id = 'PAY-OLDER'");
            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-OTHER' "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 0 "
                    + "WHERE payment_order_id = 'PAY-LEGACY-BLANK'");
            session.clearCache();

            assertEquals(0, mapper.findExpiredPayments(100).size());
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-LEGACY-BLANK"));
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(0, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldNotClosePaymentWithResidualSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET success_at=NOW() "
                    + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-MANUAL-CLOSEABLE')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertFalse(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
            mapper.recordExpiryCloseFailure("PAY-OLDER", "stale retry", 60, 2, 3600);
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            statement.executeUpdate("UPDATE t_payment_order SET success_at=NULL "
                    + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-MANUAL-CLOSEABLE')");
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
        }
    }

    @Test
    void shouldNotClosePaymentWithCaseVariantOpenStatus() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='prepay_created' "
                    + "WHERE payment_order_id='PAY-OLDER'");
            statement.executeUpdate("UPDATE t_payment_order SET status='risk_review' "
                    + "WHERE payment_order_id='PAY-MANUAL-CLOSEABLE'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertFalse(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
            mapper.recordExpiryCloseFailure("PAY-OLDER", "stale retry", 60, 2, 3600);
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            statement.executeUpdate("UPDATE t_payment_order SET status='PREPAY_CREATED' "
                    + "WHERE payment_order_id='PAY-OLDER'");
            statement.executeUpdate("UPDATE t_payment_order SET status='RISK_REVIEW' "
                    + "WHERE payment_order_id='PAY-MANUAL-CLOSEABLE'");
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
        }
    }

    @Test
    void shouldIgnoreCaseVariantPrepayAndAttemptWhenClosingExpiredPayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, created_at, expires_at) "
                    + "VALUES ('pay-older', NOW(), TIMESTAMPADD(MINUTE, 30, NOW()))");

            assertTrue(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) VALUES ('pay-older')");
            session.clearCache();
            assertTrue(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            mapper.recordExpiryCloseFailure("pay-older", "alias retry", 60, 2, 3600);
            session.clearCache();
            assertTrue(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("pay-older"));
            assertEquals(1, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
            assertEquals(1, mapper.updatePrepayStatusByPaymentOrderId("PAY-OLDER", "已失效", "danger"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT payment_order_id, cashier_status FROM t_prepay_order "
                            + "WHERE payment_order_id = 'PAY-OLDER' ORDER BY id")) {
                assertTrue(resultSet.next());
                assertEquals("PAY-OLDER", resultSet.getString("payment_order_id"));
                assertEquals("已失效", resultSet.getString("cashier_status"));
                assertTrue(resultSet.next());
                assertEquals("pay-older", resultSet.getString("payment_order_id"));
                assertNull(resultSet.getString("cashier_status"));
            }
            assertEquals(0, mapper.deleteExpiryCloseRetry("PAY-OLDER"));
            assertEquals(1, mapper.deleteExpiryCloseRetry("pay-older"));
        }
    }

    @Test
    void shouldNotClosePaymentUsingExpiredCaseVariantPrepay() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET expires_at = TIMESTAMPADD(MINUTE, 30, NOW()) "
                    + "WHERE payment_order_id = 'PAY-OLDER'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, created_at, expires_at) "
                    + "VALUES ('pay-older', NOW(), TIMESTAMPADD(MINUTE, -30, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertFalse(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));
        }
    }

    @Test
    void shouldKeepCanonicalExpiryRetryWhenOnlyCaseVariantHasAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "retry later", 60, 2, 3600);
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) VALUES ('pay-older')");

            assertEquals(0, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(1, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldNotCloseThroughCaseVariantPaymentIdOrBlockCloseWithVariantAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("pay-older"));

            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) "
                    + "VALUES ('pay-unsubmitted')");
            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
        }
    }

    @Test
    void shouldNotClosePaymentWhenItsLatestPrepayIsStillValid() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order("
                    + "payment_order_id, created_at, expires_at) VALUES ("
                    + "'PAY-OLDER', NOW(), TIMESTAMPADD(MINUTE, 30, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            org.junit.jupiter.api.Assertions.assertFalse(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-OLDER".equals(payment.getPaymentOrderId())));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-OLDER"));

            mapper.recordExpiryCloseFailure("PAY-OLDER", "stale retry", 60, 2, 3600);
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(0, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldUseStablePaymentOrderIdTieBreakerForExpiredPaymentBatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status = 'CLOSED' "
                    + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-LEGACY-BLANK', 'PAY-UNSUBMITTED')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, amount, status, status_type) VALUES "
                    + "('PAY-EXPIRY-STABLE-003', 'ORD-EXPIRY-STABLE-003', 68.00, 'PREPAY_CREATED', 'info'), "
                    + "('PAY-EXPIRY-STABLE-001', 'ORD-EXPIRY-STABLE-001', 68.00, 'PREPAY_CREATED', 'info'), "
                    + "('PAY-EXPIRY-STABLE-002', 'ORD-EXPIRY-STABLE-002', 68.00, 'PREPAY_CREATED', 'info')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount, expires_at) "
                    + "VALUES ('PAY-EXPIRY-STABLE-003', 'ORD-EXPIRY-STABLE-003', 68.00, "
                    + "TIMESTAMP '2026-09-12 00:00:00'), "
                    + "('PAY-EXPIRY-STABLE-001', 'ORD-EXPIRY-STABLE-001', 68.00, "
                    + "TIMESTAMP '2026-09-12 00:00:00'), "
                    + "('PAY-EXPIRY-STABLE-002', 'ORD-EXPIRY-STABLE-002', 68.00, "
                    + "TIMESTAMP '2026-09-12 00:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            List<ExpiredPaymentDTO> bounded = mapper.findExpiredPayments(2);
            assertEquals(2, bounded.size());
            assertEquals("PAY-EXPIRY-STABLE-001", bounded.get(0).getPaymentOrderId());
            assertEquals("PAY-EXPIRY-STABLE-002", bounded.get(1).getPaymentOrderId());

            List<ExpiredPaymentDTO> all = mapper.findExpiredPayments(100);
            assertEquals(3, all.size());
            assertEquals("PAY-EXPIRY-STABLE-001", all.get(0).getPaymentOrderId());
            assertEquals("PAY-EXPIRY-STABLE-002", all.get(1).getPaymentOrderId());
            assertEquals("PAY-EXPIRY-STABLE-003", all.get(2).getPaymentOrderId());
        }
    }

    @Test
    void shouldCreateUnsubmittedPaymentWithNullChannelTransactionNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            mapper.insertPaymentOrder("PAY-NEW", "ORD-NEW", "客户", new java.math.BigDecimal("10.00"),
                    "wx_h5");

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT channel_transaction_no FROM t_payment_order "
                                 + "WHERE payment_order_id = 'PAY-NEW'")) {
                resultSet.next();
                org.junit.jupiter.api.Assertions.assertNull(resultSet.getString(1));
            }
        }
    }

    @Test
    void shouldBackOffRepeatedExpiryCloseFailuresWithoutBlockingLaterCandidates() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            mapper.recordExpiryCloseFailure("PAY-OLDER", "first failure", 60, 2, 3600);
            assertEquals(2, mapper.findExpiredPayments(100).size());
            org.junit.jupiter.api.Assertions.assertTrue(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-UNSUBMITTED".equals(payment.getPaymentOrderId())));
            org.junit.jupiter.api.Assertions.assertTrue(mapper.findExpiredPayments(100).stream()
                    .anyMatch(payment -> "PAY-LEGACY-BLANK".equals(payment.getPaymentOrderId())));

            mapper.recordExpiryCloseFailure("PAY-OLDER", "second failure", 60, 2, 3600);
            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT retry_count, retry_interval_seconds, last_error_message "
                                 + "FROM t_payment_expiry_close_retry "
                                 + "WHERE payment_order_id = 'PAY-OLDER'")) {
                resultSet.next();
                assertEquals(2, resultSet.getInt("retry_count"));
                assertEquals(120, resultSet.getInt("retry_interval_seconds"));
                assertEquals("second failure", resultSet.getString("last_error_message"));
            }

            assertEquals(1, mapper.deleteExpiryCloseRetry("PAY-OLDER"));
            assertEquals(3, mapper.findExpiredPayments(100).size());
        }
    }

    @Test
    void shouldBoundCleanupOfRetryMarkersThatLostExpiryCloseEligibility() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "first failure", 60, 2, 3600);
            mapper.recordExpiryCloseFailure("PAY-UNSUBMITTED", "second failure", 60, 2, 3600);

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order SET status = 'SUCCESS' "
                        + "WHERE payment_order_id = 'PAY-OLDER'");
                statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) "
                        + "VALUES ('PAY-UNSUBMITTED')");
            }

            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(1));
            assertEquals(1, countExpiryCloseRetries(session));
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(1));
            assertEquals(0, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldKeepRetryMarkerWhilePaymentRemainsEligibleForExpiryClose() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "first failure", 60, 2, 3600);

            assertEquals(0, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(1, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldOnlyRecoverLegacyExpiryCloseFromTrustedChannelSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-MANUAL-CLOSED", "WX-MANUAL"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-EXPIRED-CLOSED", "WX-EXPIRED"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY-MANUAL"));

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT status FROM t_payment_order "
                                 + "WHERE payment_order_id = 'PAY-EXPIRED-CLOSED'")) {
                resultSet.next();
                assertEquals("CLOSED", resultSet.getString(1));
            }
        }
    }

    @Test
    void shouldSettleCanonicalPaymentDespiteCaseVariantPrepayAndAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('pay-submitted', 'ORD-2', 68.00)");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, channel_code, attempt_status, created_at) "
                    + "VALUES ('ATT-CASE-VARIANT', 'pay-submitted', 'other_channel', '处理中', "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("pay-submitted", "WX-ALIAS"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-CANONICAL"));
        }
    }

    @Test
    void shouldNotSettlePaymentWithOnlyCaseVariantPrepayOwnership() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-DETACHED' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('pay-submitted', 'ORD-2', 68.00)");

            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-ALIAS"));
        }
    }

    @Test
    void shouldNotRecoverClosedPaymentFromCaseVariantCloseEvent() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_payment_event "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            statement.executeUpdate("INSERT INTO t_payment_event(payment_order_id, event_type) "
                    + "VALUES ('pay-legacy-submitted-closed', 'PAYMENT_CLOSED')");

            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .markPaymentSuccessFromChannelResult("PAY-LEGACY-SUBMITTED-CLOSED", "WX-ALIAS"));
        }
    }

    @Test
    void shouldRequireExactClosedEventProvenanceForPaymentRecovery() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("UPDATE t_payment_event SET biz_no='ord-5' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_event SET biz_no='ORD-5', "
                    + "downstream_system='ACCOUNTING-SYSTEM' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_event SET downstream_system='accounting-system', "
                    + "event_topic='PAYMENT.TRADE.CLOSED.V1' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_event SET event_topic='payment.trade.closed.v1', "
                    + "event_type='payment_closed' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_event SET event_type='PAYMENT_CLOSED' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
        }
    }

    @Test
    void shouldRequireExactExpiredCloseEventProvenanceForPaymentRecovery() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET event_type='payment_expired_closed', "
                    + "downstream_system='payment-core-ops', event_topic='payment.trade.expired-closed.v1' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_event SET event_type='PAYMENT_EXPIRED_CLOSED' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
        }
    }

    @Test
    void shouldNotRecoverExpiredClosedPaymentThroughTerminalAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_event SET event_type='PAYMENT_EXPIRED_CLOSED', "
                    + "downstream_system='payment-core-ops', event_topic='payment.trade.expired-closed.v1' "
                    + "WHERE payment_order_id='PAY-LEGACY-SUBMITTED-CLOSED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='失败', completed_at=NOW() "
                    + "WHERE attempt_no='ATT-LEGACY-CLOSED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at=NULL "
                    + "WHERE attempt_no='ATT-LEGACY-CLOSED'");
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='处理中' "
                    + "WHERE attempt_no='ATT-LEGACY-CLOSED'");
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
        }
    }

    @Test
    void shouldFinalizeOnlyCanonicalLatestAttemptAfterPaymentSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', success_at=NOW() "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, channel_code, attempt_status, created_at) "
                    + "VALUES ('ATT-CASE-VARIANT', 'pay-submitted', 'wx_h5', '处理中', "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.finalizeLatestPaymentAttemptSuccess("pay-submitted"));
            assertEquals(1, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT attempt_no, attempt_status FROM t_payment_attempt "
                            + "WHERE payment_order_id='PAY-SUBMITTED' "
                            + "ORDER BY id DESC LIMIT 2")) {
                assertTrue(resultSet.next());
                assertEquals("ATT-CASE-VARIANT", resultSet.getString("attempt_no"));
                assertEquals("处理中", resultSet.getString("attempt_status"));
                assertTrue(resultSet.next());
                assertEquals("ATT-SUBMITTED-LATEST", resultSet.getString("attempt_no"));
                assertEquals("成功", resultSet.getString("attempt_status"));
            }
        }
    }

    @Test
    void shouldRejectSuccessAndPendingCallbackWhenLatestAttemptBelongsToAnotherChannel() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE attempt_no IN ('ATT-SUBMITTED-LATEST', 'ATT-LEGACY-CLOSED')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5' "
                    + "WHERE attempt_no IN ('ATT-SUBMITTED-LATEST', 'ATT-LEGACY-CLOSED')");
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(1, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldNotApplyCallbackResultWithoutChannelTransactionNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", null));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "  "));
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", null));
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "  "));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status, channel_transaction_no, success_at FROM t_payment_order "
                            + "WHERE payment_order_id='PAY-SUBMITTED'")) {
                assertTrue(resultSet.next());
                assertEquals("WAIT_CALLBACK", resultSet.getString("status"));
                assertNull(resultSet.getString("channel_transaction_no"));
                assertNull(resultSet.getTimestamp("success_at"));
            }
            assertEquals(1, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-VALID"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-VALID"));
        }
    }

    @Test
    void shouldNotAcceptCaseVariantAttemptChannelOnCallbackTransition() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='WX_H5' "
                    + "WHERE attempt_no IN ('ATT-SUBMITTED-LATEST', 'ATT-LEGACY-CLOSED')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUCCESS"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5' "
                    + "WHERE attempt_no IN ('ATT-SUBMITTED-LATEST', 'ATT-LEGACY-CLOSED')");
            assertEquals(1, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY"));
        }
    }

    @Test
    void shouldNotFinalizeLatestAttemptAfterPaymentChannelDrifts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', success_at=NOW() "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");

            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldOnlyFinalizeAttemptFromCanonicalSuccessState() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='success', success_at=NOW() "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            assertEquals(1, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldOnlyFinalizeAttemptForExactBoundChannel() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', success_at=NOW(), "
                    + "channel_code='WX_H5' WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
            statement.executeUpdate("UPDATE t_payment_order SET channel_code='wx_h5' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            assertEquals(1, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldNotRecoverClosedPaymentWhenLatestAttemptIsTerminal() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '失败', "
                    + "attempt_status_type = 'danger' "
                    + "WHERE attempt_no = 'ATT-LEGACY-CLOSED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY-STALE"));

            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id = 'PAY-LEGACY-SUBMITTED-CLOSED'")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("CLOSED", resultSet.getString("status"));
                org.junit.jupiter.api.Assertions.assertNull(resultSet.getString("channel_transaction_no"));
            }
        }
    }

    @Test
    void shouldNotLetPendingCallbackOverwriteFinalPaymentState() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.markPaymentPendingFromCallback(
                    "PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-SUBMITTED", "WX-OTHER-PENDING"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-FINAL-SUCCESS", "WX-LATE-PENDING"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-MANUAL-CLOSED", "WX-LATE-PENDING"));

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT status, channel_transaction_no FROM t_payment_order "
                                 + "WHERE payment_order_id = 'PAY-SUBMITTED'")) {
                resultSet.next();
                assertEquals("WAIT_CALLBACK", resultSet.getString("status"));
                assertEquals("WX-PENDING", resultSet.getString("channel_transaction_no"));
            }
        }
    }

    @Test
    void shouldNotOverwriteExistingChannelTransactionOnCallbackTransition() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no='WX-ORIGINAL' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "wx-original"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "wx-original"));
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-OTHER"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-OTHER"));
            try (java.sql.ResultSet payment = statement.executeQuery(
                    "SELECT status, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id='PAY-SUBMITTED'")) {
                assertTrue(payment.next());
                assertEquals("WAIT_CALLBACK", payment.getString("status"));
                assertEquals("WX-ORIGINAL", payment.getString("channel_transaction_no"));
            }
            assertEquals(1, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-ORIGINAL"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-ORIGINAL"));
        }
    }

    @Test
    void shouldNotAcceptCaseVariantPendingPaymentStatus() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='wait_callback' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUCCESS"));
        }
    }

    @Test
    void shouldNotRegressTerminalLatestAttemptOnPendingCallback() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '失败', "
                    + "attempt_status_type = 'danger' "
                    + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-SUBMITTED", "WX-PENDING-STALE"));
            assertEquals(0, mapper.updateLatestPaymentAttemptStatus(
                    "PAY-SUBMITTED", "待回调", "warn"));

            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status FROM t_payment_order WHERE payment_order_id = 'PAY-SUBMITTED'")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("WAIT_CALLBACK", resultSet.getString("status"));
            }
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT attempt_status, attempt_status_type FROM t_payment_attempt "
                            + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("失败", resultSet.getString("attempt_status"));
                assertEquals("danger", resultSet.getString("attempt_status_type"));
            }
        }
    }

    @Test
    void shouldRejectEnglishAndPaddedTerminalAttemptInAtomicTransitions() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            for (String terminalStatus : java.util.Arrays.asList("SUCCESS", "FAIL", "FAILED", " 成功 ", "支付成功")) {
                statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='" + terminalStatus
                        + "' WHERE attempt_no IN ('ATT-SUBMITTED-LATEST', 'ATT-LEGACY-CLOSED')");

                assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-TERMINAL"));
                assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-TERMINAL"));
                assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                        "PAY-LEGACY-SUBMITTED-CLOSED", "WX-TERMINAL"));
                assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                        "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-TERMINAL"));
                assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                        "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-TERMINAL"));
                assertEquals(0, mapper.updateLatestPaymentAttemptStatus("PAY-SUBMITTED", "待回调", "warn"));

                statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', success_at=NOW() "
                        + "WHERE payment_order_id='PAY-SUBMITTED'");
                assertEquals(0, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
                statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK', success_at=NULL "
                        + "WHERE payment_order_id='PAY-SUBMITTED'");
            }
        }
    }

    @Test
    void shouldPreserveFastCallbackFinalStateWhenSubmitResultArrivesLater() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET payment_method = '微信支付', "
                    + "channel_code = 'wx_h5' WHERE payment_order_id = 'PAY-FINAL-SUCCESS'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-FINAL-SUCCESS", "ATT-FINAL-SUCCESS", "微信支付", "wx_h5", "WX-SUCCESS"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-FINAL-SUCCESS", "ATT-FINAL-SUCCESS", "微信支付", "WX-SUCCESS"));
            assertEquals(1, mapper.updatePaymentAttemptSubmitResult(
                    "ATT-FINAL-SUCCESS", "{\"code\":\"ACCEPTED\"}", "待回调", "warn"));

            try (java.sql.ResultSet payment = statement.executeQuery(
                    "SELECT status, status_type, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id = 'PAY-FINAL-SUCCESS'")) {
                org.junit.jupiter.api.Assertions.assertTrue(payment.next());
                assertEquals("SUCCESS", payment.getString("status"));
                assertEquals("success", payment.getString("status_type"));
                assertEquals("WX-SUCCESS", payment.getString("channel_transaction_no"));
            }
            try (java.sql.ResultSet attempt = statement.executeQuery(
                    "SELECT response_payload, attempt_status, attempt_status_type, completed_at "
                            + "FROM t_payment_attempt WHERE attempt_no = 'ATT-FINAL-SUCCESS'")) {
                org.junit.jupiter.api.Assertions.assertTrue(attempt.next());
                assertEquals("{\"code\":\"ACCEPTED\"}", attempt.getString("response_payload"));
                assertEquals("成功", attempt.getString("attempt_status"));
                assertEquals("success", attempt.getString("attempt_status_type"));
                org.junit.jupiter.api.Assertions.assertNotNull(attempt.getTimestamp("completed_at"));
            }
        }
    }

    @Test
    void shouldRecordGatewayOncePerAttemptAndRejectConflicts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(1, mapper.recordPaymentAttemptGateway("ATT-FINAL-SUCCESS", "GW_WX"));
            assertEquals(1, mapper.recordPaymentAttemptGateway("ATT-FINAL-SUCCESS", "GW_WX"));
            assertEquals(0, mapper.recordPaymentAttemptGateway("ATT-FINAL-SUCCESS", "GW_ALI"));
            assertEquals(0, mapper.recordPaymentAttemptGateway("ATT-FINAL-SUCCESS", "gw_wx"));
            assertEquals(0, mapper.recordPaymentAttemptGateway("ATT-FINAL-SUCCESS", "GW_WX "));
            assertEquals(0, mapper.recordPaymentAttemptGateway("ATT-UNKNOWN", "GW_WX"));
            try (java.sql.ResultSet attempt = statement.executeQuery(
                    "SELECT gateway_code FROM t_payment_attempt WHERE attempt_no = 'ATT-FINAL-SUCCESS'")) {
                org.junit.jupiter.api.Assertions.assertTrue(attempt.next());
                assertEquals("GW_WX", attempt.getString("gateway_code"));
            }
        }
    }

    @Test
    void shouldNotUpdateAttemptResultGatewayOrSnapshotThroughCaseVariantNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updatePaymentAttemptSubmitResult(
                    "att-submitted-latest", "alias-response", "待回调", "warn"));
            assertEquals(0, mapper.recordPaymentAttemptGateway("att-submitted-latest", "GW_WX"));
            assertEquals(0, mapper.updateAttemptOpsConfigSnapshotVersion("att-submitted-latest", 7L));
            assertEquals(1, mapper.updatePaymentAttemptSubmitResult(
                    "ATT-SUBMITTED-LATEST", "owner-response", "待回调", "warn"));
            assertEquals(1, mapper.recordPaymentAttemptGateway("ATT-SUBMITTED-LATEST", "GW_WX"));
            assertEquals(1, mapper.updateAttemptOpsConfigSnapshotVersion("ATT-SUBMITTED-LATEST", 7L));
        }
    }

    @Test
    void shouldNotOverwriteTerminalOrBlankAttemptWithoutCompletedTimeOnLateSubmitResult() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            for (String terminalStatus : new String[] {
                    "成功", "支付成功", " 支付成功 ", "失败", "支付失败", "SUCCESS", "FAIL", "FAILED", " "}) {
                statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='" + terminalStatus
                        + "', attempt_status_type='success' WHERE attempt_no='ATT-SUBMITTED-LATEST'");
                assertEquals(0, mapper.updatePaymentAttemptSubmitResult(
                        "ATT-SUBMITTED-LATEST", "late-response", "待回调", "warn"));
                try (java.sql.ResultSet attempt = statement.executeQuery(
                        "SELECT attempt_status, response_payload FROM t_payment_attempt "
                                + "WHERE attempt_no='ATT-SUBMITTED-LATEST'")) {
                    assertTrue(attempt.next());
                    assertEquals(terminalStatus, attempt.getString("attempt_status"));
                    assertNull(attempt.getString("response_payload"));
                }
            }
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='处理中', "
                    + "attempt_status_type='warn' WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            assertEquals(1, mapper.updatePaymentAttemptSubmitResult(
                    "ATT-SUBMITTED-LATEST", "accepted-response", "待回调", "warn"));
        }
    }

    @Test
    void shouldNotReusePaymentAttemptThroughCaseVariantIdempotencyKey() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt "
                    + "SET prepay_order_no='PRE-SUBMITTED', idempotency_key='IDEMP-001' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertFalse(mapper.existsPaymentAttemptByIdempotencyKey("idemp-001"));
            assertNull(mapper.findPaymentAttemptIdempotency("idemp-001"));
            assertTrue(mapper.existsPaymentAttemptByIdempotencyKey("IDEMP-001"));
            assertEquals("PRE-SUBMITTED", mapper.findPaymentAttemptIdempotency("IDEMP-001")
                    .getPrepayOrderNo());
        }
    }

    @Test
    void shouldNotTruncateLongMultibyteIdempotencyKeyDuringExactLookup() throws Exception {
        String prefix = String.join("", java.util.Collections.nCopies(86, "界"));
        String storedKey = prefix + "A";
        String aliasKey = prefix + "a";
        try (SqlSession session = sqlSessionFactory.openSession();
             java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                     "UPDATE t_payment_attempt SET idempotency_key = ? "
                             + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'")) {
            statement.setString(1, storedKey);
            assertEquals(1, statement.executeUpdate());
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertFalse(mapper.existsPaymentAttemptByIdempotencyKey(aliasKey));
            assertNull(mapper.findPaymentAttemptIdempotency(aliasKey));
            assertTrue(mapper.existsPaymentAttemptByIdempotencyKey(storedKey));
        }
    }

    @Test
    void shouldRejectPaymentMethodBindingAfterPaymentIsFinalized() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-FINAL-SUCCESS", "PRE-FINAL-SUCCESS", "微信支付", "wx_h5", "WX-LATE"));
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-MANUAL-CLOSED", "PRE-MANUAL-CLOSED", "微信支付", "wx_h5", "WX-LATE"));
        }
    }

    @Test
    void shouldBindChannelOnlyForOneMatchingPrepayAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00 "
                    + "WHERE payment_order_id IN ('PAY-UNSUBMITTED', 'PAY-OLDER')");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 67.00 "
                    + "WHERE payment_order_id = 'PAY-OLDER'");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 68.00, order_no = 'ORD-1', "
                    + "prepay_order_no = 'PRE-UNSUBMITTED' "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-OLDER", "PRE-OLDER", "微信支付", "wx_h5", null));
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-OLDER'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('PAY-OLDER', 'ORD-0', 68.00)");
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-OLDER", "PRE-OLDER", "微信支付", "wx_h5", null));
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            try (java.sql.ResultSet payment = statement.executeQuery(
                    "SELECT payment_order_id, status FROM t_payment_order "
                            + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-UNSUBMITTED') "
                            + "ORDER BY payment_order_id")) {
                assertTrue(payment.next());
                assertEquals("PAY-OLDER", payment.getString("payment_order_id"));
                assertEquals("PREPAY_CREATED", payment.getString("status"));
                assertTrue(payment.next());
                assertEquals("PAY-UNSUBMITTED", payment.getString("payment_order_id"));
                assertEquals("WAIT_CALLBACK", payment.getString("status"));
            }
        }
    }

    @Test
    void shouldNotPrepareSubmitOverExistingChannelOrSuccessTrace() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00, "
                    + "channel_transaction_no = 'WX-EXISTING' "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no = 'PRE-UNSUBMITTED', "
                    + "order_no = 'ORD-1', amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no = NULL, "
                    + "success_at = NOW() WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            statement.executeUpdate("UPDATE t_payment_order SET success_at = NULL "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", "WX-EARLY"));
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            session.rollback();
        }
    }

    @Test
    void shouldBindCanonicalPaymentDespiteCaseVariantPrepayWithoutAcceptingPaymentAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount=68.00 "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no='PRE-UNSUBMITTED', "
                    + "order_no='ORD-1', amount=68.00 WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, amount) "
                    + "VALUES ('PRE-CASE-VARIANT', 'pay-unsubmitted', 'ORD-1', 68.00)");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "pay-unsubmitted", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
        }
    }

    @Test
    void shouldNotBindChannelUsingOnlyCaseVariantPrepayOwnership() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount=68.00 "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-DETACHED' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, amount) "
                    + "VALUES ('PRE-CASE-VARIANT', 'pay-unsubmitted', 'ORD-1', 68.00)");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-CASE-VARIANT", "微信支付", "wx_h5", null));
        }
    }

    @Test
    void shouldRejectChannelBindingForCaseVariantPrepayOrderNumber() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount=68.00 "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET amount=68.00, order_no='ord-1', "
                    + "prepay_order_no='PRE-UNSUBMITTED' WHERE payment_order_id='PAY-UNSUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertFalse(mapper.findPrepay("PRE-UNSUBMITTED").getPrepayAmountBindingValid());
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
            statement.executeUpdate("UPDATE t_prepay_order SET order_no='ORD-1' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            session.clearCache();
            assertTrue(mapper.findPrepay("PRE-UNSUBMITTED").getPrepayAmountBindingValid());
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
        }
    }

    @Test
    void shouldRejectCaseVariantPrepayIdentifierAcrossReadsAndChannelBinding() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount=68.00 "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET amount=68.00, order_no='ORD-1', "
                    + "prepay_order_no='PRE-UNSUBMITTED' WHERE payment_order_id='PAY-UNSUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertTrue(mapper.findPrepay("PRE-UNSUBMITTED") != null);
            assertTrue(mapper.isPrepayExpired("PRE-UNSUBMITTED"));
            assertNull(mapper.findPrepay("pre-unsubmitted"));
            assertNull(mapper.findPaymentOrderIdByPrepayOrderNo("pre-unsubmitted"));
            assertNull(mapper.findOrderNoByPrepayOrderNo("pre-unsubmitted"));
            assertFalse(mapper.isPrepayExpired("pre-unsubmitted"));
            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "pre-unsubmitted", "微信支付", "wx_h5", null));
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-UNSUBMITTED", "微信支付", "wx_h5", null));
        }
    }

    @Test
    void shouldNotAttachCaseVariantPrepayToCashierOrInvalidateCanonicalBinding() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET amount=68.00 "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no='PRE-UNSUBMITTED', "
                    + "order_no='ORD-1', amount=68.00, expires_at=TIMESTAMPADD(MINUTE, 30, NOW()) "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, amount, created_at, expires_at) "
                    + "VALUES ('PRE-CASE-VARIANT', 'pay-unsubmitted', 'ORD-ALIAS', 68.00, "
                    + "NOW(), TIMESTAMPADD(MINUTE, -1, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertTrue(mapper.findPrepay("PRE-UNSUBMITTED").getPrepayAmountBindingValid());
            assertTrue(mapper.findLatestActivePrepayByOrderNo("ORD-1").getPrepayAmountBindingValid());
            assertNull(mapper.findPrepay("PRE-CASE-VARIANT"));
            assertFalse(mapper.isPrepayExpired("PRE-CASE-VARIANT"));
            statement.executeUpdate("UPDATE t_prepay_order SET expires_at=TIMESTAMPADD(MINUTE, 30, NOW()) "
                    + "WHERE prepay_order_no='PRE-CASE-VARIANT'");
            session.clearCache();
            assertNull(mapper.findLatestActivePrepayByOrderNo("ORD-ALIAS"));
        }
    }

    @Test
    void shouldNotRegressFinalPaymentStatusThroughRiskUpdate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePaymentRiskStatus(
                    "PAY-FINAL-SUCCESS", "RISK_REVIEW", "warn"));
            assertEquals(0, mapper.updatePaymentRiskStatus(
                    "PAY-MANUAL-CLOSED", "RISK_REJECTED", "danger"));

            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT payment_order_id, status FROM t_payment_order "
                            + "WHERE payment_order_id IN ('PAY-FINAL-SUCCESS', 'PAY-MANUAL-CLOSED') "
                            + "ORDER BY payment_order_id")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("PAY-FINAL-SUCCESS", resultSet.getString("payment_order_id"));
                assertEquals("SUCCESS", resultSet.getString("status"));
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("PAY-MANUAL-CLOSED", resultSet.getString("payment_order_id"));
                assertEquals("CLOSED", resultSet.getString("status"));
            }
        }
    }

    @Test
    void shouldOnlyApplyRiskStatusBeforeAnyChannelAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-SUBMITTED", "RISK_REVIEW", "warn"));
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));

            statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "SUCCESS", "success"));
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "danger"));
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "risk_review", "warn"));
            statement.executeUpdate("UPDATE t_payment_order SET status='wait_callback' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));
            statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK', success_at=NOW() "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));
            statement.executeUpdate("UPDATE t_payment_order SET success_at=NULL, "
                    + "channel_transaction_no='WX-EARLY' WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertEquals(0, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));
            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no=NULL "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertEquals(1, mapper.updatePaymentRiskStatus("PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id='PAY-UNSUBMITTED'")) {
                assertTrue(resultSet.next());
                assertEquals("RISK_REVIEW", resultSet.getString("status"));
                assertNull(resultSet.getString("channel_transaction_no"));
            }
            statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK' "
                    + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-MANUAL-CLOSEABLE')");
            assertEquals(1, mapper.updatePaymentRiskStatus("PAY-OLDER", "RISK_BLOCKED", "danger"));
            assertEquals(1, mapper.updatePaymentRiskStatus("PAY-MANUAL-CLOSEABLE", "RISK_REJECTED", "danger"));
        }
    }

    @Test
    void shouldNotUpdatePaymentRiskStatusThroughCaseVariantIdentifier() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.updatePaymentRiskStatus(
                    "pay-unsubmitted", "RISK_REVIEW", "warn"));
            assertEquals(1, mapper.updatePaymentRiskStatus(
                    "PAY-UNSUBMITTED", "RISK_REVIEW", "warn"));
        }
    }

    @Test
    void shouldUpdateOnlyExactPrepayToPaying() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no='PRE-UNSUBMITTED', "
                    + "cashier_status='待支付' WHERE payment_order_id='PAY-UNSUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.updatePrepayToPaying("pre-unsubmitted"));
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, cashier_status) "
                    + "VALUES ('pre-unsubmitted', 'pay-unsubmitted', '待支付')");
            assertEquals(1, mapper.updatePrepayToPaying("PRE-UNSUBMITTED"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT prepay_order_no, cashier_status FROM t_prepay_order "
                            + "WHERE prepay_order_no='PRE-UNSUBMITTED' ORDER BY id")) {
                assertTrue(resultSet.next());
                assertEquals("PRE-UNSUBMITTED", resultSet.getString("prepay_order_no"));
                assertEquals("支付中", resultSet.getString("cashier_status"));
                assertTrue(resultSet.next());
                assertEquals("pre-unsubmitted", resultSet.getString("prepay_order_no"));
                assertEquals("待支付", resultSet.getString("cashier_status"));
            }
        }
    }

    @Test
    void shouldNotReadOccupyOrReleaseSubmitTokenThroughCaseVariantIdentifiers() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_submit_concurrency_token "
                    + "(prepay_order_no, payment_order_id, source_app_id, token_status, "
                    + "holder_idempotency_key, holder_terminal, holder_client_ip, "
                    + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                    + "('PRE-TOKEN-001', 'PAY-TOKEN-001', 'app', 'RELEASED', "
                    + "'initial-key', 'APP', '127.0.0.1', NOW(), "
                    + "TIMESTAMPADD(MINUTE, -1, NOW()), NOW(), NOW())");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertNull(mapper.findSubmitConcurrencyToken("pre-token-001", "app"));
            assertNull(mapper.findSubmitConcurrencyToken("PRE-TOKEN-001", "APP"));
            assertEquals("PRE-TOKEN-001", mapper.findSubmitConcurrencyToken("PRE-TOKEN-001", "app")
                    .getPrepayOrderNo());
            assertEquals(0, mapper.occupySubmitConcurrencyToken(
                    "pre-token-001", "PAY-TOKEN-001", "app", "alias-key", "APP", "127.0.0.1", 60));
            assertEquals(0, mapper.occupySubmitConcurrencyToken(
                    "PRE-TOKEN-001", "PAY-TOKEN-001", "APP", "alias-key", "APP", "127.0.0.1", 60));
            assertEquals(1, mapper.occupySubmitConcurrencyToken(
                    "PRE-TOKEN-001", "PAY-TOKEN-001", "app", "owner-key", "APP", "127.0.0.1", 60));
            assertEquals(0, mapper.releaseSubmitConcurrencyToken("pay-token-001", "ALIAS_RELEASE"));
            assertEquals(1, mapper.releaseSubmitConcurrencyToken("PAY-TOKEN-001", "OWNER_RELEASE"));
        }
    }

    @Test
    void shouldRejectChannelBindingWhenOriginalPrepayIsReplaced() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no = 'PRE-ORIGINAL', "
                    + "order_no = 'ORD-1', amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'PAY-OTHER' "
                    + "WHERE prepay_order_no = 'PRE-ORIGINAL'");
            statement.executeUpdate("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, payment_order_id, order_no, amount) "
                    + "VALUES ('PRE-REPLACEMENT', 'PAY-UNSUBMITTED', 'ORD-1', 68.00)");

            assertEquals(0, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-ORIGINAL", "微信支付", "wx_h5", null));
            assertEquals(1, mapper.updatePaymentMethodAndChannel(
                    "PAY-UNSUBMITTED", "PRE-REPLACEMENT", "微信支付", "wx_h5", null));
        }
    }

    @Test
    void shouldBindAndRecoverSubmitResultWithoutRegressingPaymentState() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET payment_method = '微信支付', "
                    + "channel_code = 'wx_h5' WHERE payment_order_id = 'PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-SUBMIT-BOUND"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-ABNORMAL-WAIT", "ATT-MISSING", "微信支付", null));

            try (java.sql.ResultSet bound = statement.executeQuery(
                    "SELECT status, status_type, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id = 'PAY-SUBMITTED'")) {
                org.junit.jupiter.api.Assertions.assertTrue(bound.next());
                assertEquals("WAIT_CALLBACK", bound.getString("status"));
                assertEquals("warn", bound.getString("status_type"));
                assertEquals("WX-SUBMIT-BOUND", bound.getString("channel_transaction_no"));
            }
            try (java.sql.ResultSet unknown = statement.executeQuery(
                    "SELECT status, status_type, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id = 'PAY-ABNORMAL-WAIT'")) {
                org.junit.jupiter.api.Assertions.assertTrue(unknown.next());
                assertEquals("WAIT_CALLBACK", unknown.getString("status"));
                assertEquals("warn", unknown.getString("status_type"));
                org.junit.jupiter.api.Assertions.assertNull(
                        unknown.getString("channel_transaction_no"));
            }
        }
    }

    @Test
    void shouldNotApplySubmitOrCallbackResultWithResidualSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET success_at=NOW() "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-SUBMITTED"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-SUBMITTED"));
            assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUBMITTED"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status, channel_transaction_no FROM t_payment_order "
                            + "WHERE payment_order_id='PAY-SUBMITTED'")) {
                assertTrue(resultSet.next());
                assertEquals("WAIT_CALLBACK", resultSet.getString("status"));
                assertNull(resultSet.getString("channel_transaction_no"));
            }
            statement.executeUpdate("UPDATE t_payment_order SET success_at=NULL "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            assertEquals(1, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-SUBMITTED"));
            assertEquals(1, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-SUBMITTED"));
            assertEquals(1, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-SUBMITTED"));
        }
    }

    @Test
    void shouldNotBindSubmitResultThroughStateChannelOrTransactionAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            statement.executeUpdate("UPDATE t_payment_order SET status = 'wait_callback' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-REFERENCE"));
            statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK', "
                    + "channel_code = 'WX_H5' WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-REFERENCE"));
            statement.executeUpdate("UPDATE t_payment_order SET channel_code = 'wx_h5', "
                    + "channel_transaction_no = 'WX-REFERENCE' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "wx-reference"));
            assertEquals(1, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-REFERENCE"));
            session.rollback();
        }
    }

    @Test
    void shouldNotMarkSubmitUnknownThroughStateOrTransactionAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("UPDATE t_payment_order SET status = 'wait_callback', "
                    + "channel_transaction_no = 'WX-REFERENCE' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");

            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-REFERENCE"));
            statement.executeUpdate("UPDATE t_payment_order SET status = 'WAIT_CALLBACK' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx-reference"));
            assertEquals(1, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-REFERENCE"));
            session.rollback();
        }
    }

    @Test
    void shouldNotBindOlderSubmitResultAfterAnotherAttemptBecomesLatest() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt(attempt_no, payment_order_id, "
                    + "channel_code, attempt_status, attempt_status_type, created_at) VALUES "
                    + "('ATT-NEWER-SUBMIT', 'PAY-SUBMITTED', 'wx_h5', '处理中', 'warn', "
                    + "TIMESTAMPADD(SECOND, 1, NOW()))");

            assertEquals(0, session.getMapper(PaymentMapper.class).bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-OLD-RESULT"));
            assertEquals(1, session.getMapper(PaymentMapper.class).bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-NEWER-SUBMIT", "微信支付", "wx_h5", "WX-NEW-RESULT"));
            session.rollback();
        }
    }

    @Test
    void shouldNotMarkOlderSubmitUnknownAfterAnotherAttemptBecomesLatest() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt(attempt_no, payment_order_id, "
                    + "channel_code, attempt_status, attempt_status_type, created_at) VALUES "
                    + "('ATT-NEWER-UNKNOWN', 'PAY-SUBMITTED', 'wx_h5', '处理中', 'warn', "
                    + "TIMESTAMPADD(SECOND, 1, NOW()))");

            assertEquals(0, session.getMapper(PaymentMapper.class).markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-OLD-UNKNOWN"));
            assertEquals(1, session.getMapper(PaymentMapper.class).markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-NEWER-UNKNOWN", "微信支付", "WX-NEW-UNKNOWN"));
            session.rollback();
        }
    }

    @Test
    void shouldRejectAliasForeignOrCompletedAttemptForSubmitResult() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "att-submitted-latest", "微信支付", "wx_h5", "WX-ALIAS-ATTEMPT"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "att-submitted-latest", "微信支付", "WX-ALIAS-ATTEMPT"));
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-LEGACY-CLOSED", "微信支付", "wx_h5", "WX-ALIEN-ATTEMPT"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-LEGACY-CLOSED", "微信支付", "WX-ALIEN-ATTEMPT"));

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at = NOW() "
                    + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'");
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-COMPLETED"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-COMPLETED"));
            session.rollback();
        }
    }

    @Test
    void shouldNotBindOrRecoverSubmitResultAfterPaymentMethodDrifts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("UPDATE t_payment_order SET payment_method = '支付宝' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");

            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-METHOD-DRIFT"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-METHOD-DRIFT"));

            statement.executeUpdate("UPDATE t_payment_order SET payment_method = 'WX_H5' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "wx_h5", "wx_h5", "WX-METHOD-ALIAS"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "wx_h5", "WX-METHOD-ALIAS"));

            statement.executeUpdate("UPDATE t_payment_order SET payment_method = '微信支付' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            assertEquals(1, mapper.bindPaymentChannelSubmitResult(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-METHOD-DRIFT"));
            assertEquals(1, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-METHOD-DRIFT"));
            session.rollback();
        }
    }

    @Test
    void shouldMatchOnlyCanonicalLatestAttemptForTerminalSubmitFallback() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertTrue(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST"));
            assertFalse(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-EARLIER"));
            assertFalse(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "att-submitted-latest"));
            assertFalse(mapper.isLatestPaymentAttemptForOrder(
                    "pay-submitted", "ATT-SUBMITTED-LATEST"));
            assertFalse(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "ATT-LEGACY-CLOSED"));

            statement.executeUpdate("INSERT INTO t_payment_attempt(attempt_no, payment_order_id, "
                    + "channel_code, attempt_status, attempt_status_type, created_at) VALUES "
                    + "('ATT-NEWER-TERMINAL', 'PAY-SUBMITTED', 'wx_h5', '成功', 'success', "
                    + "TIMESTAMPADD(SECOND, 1, NOW()))");
            session.clearCache();
            assertFalse(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST"));
            assertTrue(mapper.isLatestPaymentAttemptForOrder(
                    "PAY-SUBMITTED", "ATT-NEWER-TERMINAL"));
            session.rollback();
        }
    }

    @Test
    void shouldRejectCaseVariantPaymentIdForSubmitResultAndPendingCallback() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                    "pay-submitted", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-ALIAS"));
            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "pay-submitted", "ATT-SUBMITTED-LATEST", "微信支付", "WX-ALIAS"));
            assertEquals(0, mapper.markPaymentPendingFromCallback("pay-submitted", "WX-ALIAS"));
            assertEquals(0, mapper.updateLatestPaymentAttemptStatus("pay-submitted", "待回调", "warn"));
        }
    }

    @Test
    void shouldUseOnlyCanonicalLatestAttemptForSubmitRecoveryAndPendingCallback() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, channel_code, attempt_status, completed_at, created_at) "
                    + "VALUES ('ATT-CASE-VARIANT', 'pay-submitted', 'wx_h5', '失败', NOW(), "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()))");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-PENDING"));
            assertEquals(1, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-PENDING"));
            assertEquals(1, mapper.updateLatestPaymentAttemptStatus("PAY-SUBMITTED", "待回调", "warn"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT attempt_no, attempt_status FROM t_payment_attempt "
                            + "WHERE payment_order_id='PAY-SUBMITTED' ORDER BY id DESC LIMIT 2")) {
                assertTrue(resultSet.next());
                assertEquals("ATT-CASE-VARIANT", resultSet.getString("attempt_no"));
                assertEquals("失败", resultSet.getString("attempt_status"));
                assertTrue(resultSet.next());
                assertEquals("ATT-SUBMITTED-LATEST", resultSet.getString("attempt_no"));
                assertEquals("待回调", resultSet.getString("attempt_status"));
            }
        }
    }

    @Test
    void shouldNotRecoverSubmitResultFromCaseVariantAttemptWhenCanonicalIsTerminal() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='失败', completed_at=NOW() "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            statement.executeUpdate("INSERT INTO t_payment_attempt "
                    + "(attempt_no, payment_order_id, channel_code, attempt_status, created_at) "
                    + "VALUES ('ATT-CASE-VARIANT', 'pay-submitted', 'wx_h5', '处理中', "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()))");

            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .markPaymentSubmitResultUnknown(
                            "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-ALIAS"));
        }
    }

    @Test
    void shouldNotMarkSubmitResultUnknownWhenLatestAttemptIsTerminal() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '失败', "
                    + "attempt_status_type = 'danger' "
                    + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                    "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-SUBMIT-STALE"));

            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status, status_type, channel_transaction_no "
                            + "FROM t_payment_order WHERE payment_order_id = 'PAY-SUBMITTED'")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("WAIT_CALLBACK", resultSet.getString("status"));
                assertEquals("warn", resultSet.getString("status_type"));
                org.junit.jupiter.api.Assertions.assertNull(resultSet.getString("channel_transaction_no"));
            }
        }
    }

    @Test
    void shouldRequireSubmissionAttemptForChannelStateTransitions() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-RISK-REJECTED", "WX-NOT-SUBMITTED"));
            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-ABNORMAL-WAIT", "WX-NOT-SUBMITTED"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-RISK-REJECTED", "WX-NOT-SUBMITTED"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-ABNORMAL-WAIT", "WX-NOT-SUBMITTED"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(1, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
            assertPaymentSuccessTimePresent(session, "PAY-SUBMITTED");
            assertLatestPaymentAttemptCompleted(session, "PAY-SUBMITTED");
            assertEarlierPaymentAttemptUnchanged(session, "PAY-SUBMITTED");
        }
    }

    @Test
    void shouldAnchorSuccessfulAttemptCompletionToPaymentSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 09:59:00' "
                    + "WHERE attempt_no='ATT-SUBMITTED-EARLIER'");
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 09:59:59' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', "
                    + "status_type='success', success_at='2026-10-02 10:00:00.123' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");

            assertEquals(1, session.getMapper(PaymentMapper.class)
                    .finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));

            try (java.sql.ResultSet result = statement.executeQuery(
                    "SELECT p.success_at, a.completed_at FROM t_payment_order p "
                            + "JOIN t_payment_attempt a ON a.payment_order_id=p.payment_order_id "
                            + "WHERE a.attempt_no='ATT-SUBMITTED-LATEST'")) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                assertEquals(result.getTimestamp("success_at"), result.getTimestamp("completed_at"));
            }
            session.rollback();
        }
    }

    @Test
    void shouldCompareRawPaymentSuccessTimelineWithoutExposingValidationFlag() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            for (String column : new String[]{"last_channel_return_code VARCHAR(64)",
                    "last_standardized_code VARCHAR(64)", "last_standardized_message VARCHAR(256)",
                    "last_handling_suggestion VARCHAR(256)", "last_retryable BOOLEAN",
                    "last_manual_intervention_required BOOLEAN", "last_mapping_version VARCHAR(64)",
                    "last_mapping_matched BOOLEAN"}) {
                statement.execute("ALTER TABLE t_payment_order ADD COLUMN " + column);
            }
            for (String column : new String[]{"terminal VARCHAR(32)", "client_ip VARCHAR(64)",
                    "request_payload VARCHAR(2048)"}) {
                statement.execute("ALTER TABLE t_payment_attempt ADD COLUMN " + column);
            }
            statement.execute("CREATE TABLE t_bill (bill_no VARCHAR(64))");
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 09:59:59.997' "
                    + "WHERE attempt_no='ATT-SUBMITTED-EARLIER'");
            statement.executeUpdate("UPDATE t_payment_order SET channel_code='wx_h5', "
                    + "channel_transaction_no='WX-RAW-001', created_at='2026-10-02 09:59:59.999', "
                    + "success_at='2026-10-02 10:00:00.123', status='SUCCESS', status_type='success' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status='成功', "
                    + "attempt_status_type='success', channel_code='wx_h5', "
                    + "created_at='2026-10-02 09:59:59.999', completed_at='2026-10-02 10:00:00.124' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");

            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentDetailDTO invalid = mapper.findDetail("PAY-SUBMITTED");
            assertEquals("2026-10-02 10:00:00", invalid.getSuccessAt());
            assertEquals(invalid.getSuccessAt(), invalid.getLatestAttemptCompletedAt());
            assertFalse(invalid.getPaymentFactTimelineValid());
            assertTrue(invalid.getPaymentSubmissionChannelValid());
            assertFalse(new ObjectMapper().writeValueAsString(invalid)
                    .contains("paymentFactTimelineValid"));
            assertFalse(new ObjectMapper().writeValueAsString(invalid)
                    .contains("paymentSubmissionChannelValid"));

            statement.executeUpdate("UPDATE t_payment_attempt "
                    + "SET completed_at='2026-10-02 10:00:00.122' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertTrue(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());

            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 10:00:00.123' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertFalse(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 09:59:59.998' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertFalse(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-10-02 09:59:59.999' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertTrue(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            PaymentDetailDTO wrongChannel = mapper.findDetail("PAY-SUBMITTED");
            assertFalse(wrongChannel.getPaymentFactTimelineValid());
            assertFalse(wrongChannel.getPaymentSubmissionChannelValid());

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='WX_H5' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            PaymentDetailDTO aliasedChannel = mapper.findDetail("PAY-SUBMITTED");
            assertFalse(aliasedChannel.getPaymentFactTimelineValid());
            assertFalse(aliasedChannel.getPaymentSubmissionChannelValid());

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertTrue(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());
            assertTrue(mapper.findDetail("PAY-SUBMITTED").getPaymentSubmissionChannelValid());

            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='支付宝' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertFalse(mapper.findDetail("PAY-SUBMITTED").getPaymentFactTimelineValid());
            assertFalse(mapper.findDetail("PAY-SUBMITTED").getPaymentSubmissionChannelValid());
        }
    }

    @Test
    void shouldNotLinkBillToPaymentDetailThroughNumberAlias() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            for (String column : new String[]{"last_channel_return_code VARCHAR(64)",
                    "last_standardized_code VARCHAR(64)", "last_standardized_message VARCHAR(256)",
                    "last_handling_suggestion VARCHAR(256)", "last_retryable BOOLEAN",
                    "last_manual_intervention_required BOOLEAN", "last_mapping_version VARCHAR(64)",
                    "last_mapping_matched BOOLEAN"}) {
                statement.execute("ALTER TABLE t_payment_order ADD COLUMN " + column);
            }
            for (String column : new String[]{"terminal VARCHAR(32)", "client_ip VARCHAR(64)",
                    "request_payload VARCHAR(2048)"}) {
                statement.execute("ALTER TABLE t_payment_attempt ADD COLUMN " + column);
            }
            statement.execute("CREATE TABLE t_bill (bill_no VARCHAR_IGNORECASE(64))");
            statement.executeUpdate("INSERT INTO t_bill (bill_no) VALUES ('BILL-001')");
            statement.executeUpdate("UPDATE t_prepay_order SET bill_no='bill-001' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertNull(mapper.findDetail("PAY-SUBMITTED").getBillNo());
            statement.executeUpdate("UPDATE t_prepay_order SET bill_no='BILL-001' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            session.clearCache();
            assertEquals("BILL-001", mapper.findDetail("PAY-SUBMITTED").getBillNo());
        }
    }

    @Test
    void shouldNotFinalizeAttemptWithoutSuccessfulPaymentFact() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
            try (java.sql.ResultSet result = statement.executeQuery(
                    "SELECT completed_at FROM t_payment_attempt "
                            + "WHERE attempt_no='ATT-SUBMITTED-LATEST'")) {
                org.junit.jupiter.api.Assertions.assertTrue(result.next());
                assertNull(result.getTimestamp("completed_at"));
            }
            session.rollback();
        }
    }

    @Test
    void shouldNotFinalizeTerminalLatestPaymentAttemptOnChannelSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '失败', "
                    + "attempt_status_type = 'danger' "
                    + "WHERE attempt_no = 'ATT-SUBMITTED-LATEST'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(0, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-SUBMITTED", "WX-SUBMITTED"));
            assertEquals(0, mapper.finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));

            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT attempt_status, attempt_status_type, completed_at "
                            + "FROM t_payment_attempt WHERE attempt_no = 'ATT-SUBMITTED-LATEST'")) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("失败", resultSet.getString("attempt_status"));
                assertEquals("danger", resultSet.getString("attempt_status_type"));
                org.junit.jupiter.api.Assertions.assertNull(resultSet.getTimestamp("completed_at"));
            }
        }
    }

    @Test
    void shouldOnlyUpdateLatestPaymentAttemptStatus() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.updateLatestPaymentAttemptStatus(
                    "PAY-SUBMITTED", "待回调", "warn"));
            assertPaymentAttemptStatuses(session, "PAY-SUBMITTED", "待回调", "warn", "待回调", "warn");
        }
    }

    @Test
    void shouldExposeLatestSubmissionFactToPaymentListAndCashier() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setPaymentOrderId("PAY-SUBMITTED");
            query.setPageNo(1);
            query.setPageSize(20);

            List<PaymentListItemDTO> items = mapper.findAll(query);
            PaymentDetailDTO submissionFact = mapper.findSubmissionFact("PAY-SUBMITTED");

            assertEquals(1, items.size());
            assertEquals("处理中", items.get(0).getLatestAttemptStatus());
            assertEquals("warn", items.get(0).getLatestAttemptStatusType());
            assertEquals(null, items.get(0).getLatestAttemptCompletedAt());
            assertEquals("WAIT_CALLBACK", submissionFact.getStatus());
            assertEquals("处理中", submissionFact.getLatestAttemptStatus());
        }
    }

    @Test
    void shouldRejectSubmissionWhenLatestAttemptPaymentMethodDiffers() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            assertTrue(mapper.findSubmissionFact("PAY-SUBMITTED").getPaymentSubmissionChannelValid());

            for (String method : new String[]{"支付宝", "微信支付 ", "微信支付x", ""}) {
                statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='" + method + "' "
                        + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
                session.clearCache();
                assertFalse(mapper.findSubmissionFact("PAY-SUBMITTED").getPaymentSubmissionChannelValid());
            }
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='ALIPAY' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='alipay' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertFalse(mapper.findSubmissionFact("PAY-SUBMITTED").getPaymentSubmissionChannelValid());
            statement.executeUpdate("UPDATE t_payment_order SET payment_method=' ' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method=' ' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertFalse(mapper.findSubmissionFact("PAY-SUBMITTED").getPaymentSubmissionChannelValid());
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='微信支付' "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='微信支付' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            session.clearCache();
            assertTrue(mapper.findSubmissionFact("PAY-SUBMITTED").getPaymentSubmissionChannelValid());
        }
    }

    @Test
    void shouldRejectPaymentMethodDriftDuringAtomicChannelTransitions() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            for (String method : new String[]{"支付宝", "微信支付 ", " "}) {
                statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='" + method + "' "
                        + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
                assertEquals(0, mapper.markPaymentPendingFromCallback("PAY-SUBMITTED", "WX-DRIFT"));
                assertEquals(0, mapper.markPaymentSuccessFromChannelResult("PAY-SUBMITTED", "WX-DRIFT"));
                assertEquals(0, mapper.bindPaymentChannelSubmitResult(
                        "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "wx_h5", "WX-DRIFT"));
                assertEquals(0, mapper.markPaymentSubmitResultUnknown(
                        "PAY-SUBMITTED", "ATT-SUBMITTED-LATEST", "微信支付", "WX-DRIFT"));
            }
        }
    }

    @Test
    void shouldNotFinalizeAttemptWhenPaymentMethodDriftsAfterSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='SUCCESS', success_at=NOW() "
                    + "WHERE payment_order_id='PAY-SUBMITTED'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='支付宝' "
                    + "WHERE attempt_no='ATT-SUBMITTED-LATEST'");
            assertEquals(0, session.getMapper(PaymentMapper.class)
                    .finalizeLatestPaymentAttemptSuccess("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldKeepPaymentListAndExportUniqueWhenPrepayAssociationsRepeat() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET pay_scene = 'HOME_CLEAN' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order("
                    + "payment_order_id, pay_scene, created_at) VALUES ("
                    + "'PAY-SUBMITTED', 'HOME_REPAIR', TIMESTAMP '2026-09-10 08:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setPaymentOrderId("PAY-SUBMITTED");
            query.setPageSize(20);

            List<PaymentListItemDTO> items = mapper.findAll(query);
            assertEquals(1, items.size());
            assertNull(items.get(0).getPayScene());
            assertEquals("MULTIPLE_PREPAY_BINDINGS", items.get(0).getPrepayBindingIssue());
            assertEquals(1L, mapper.count(query));
            assertEquals(1, mapper.findAllForExport(query).size());
            assertNull(mapper.findAllForExport(query).get(0).getPayScene());
            assertEquals("MULTIPLE_PREPAY_BINDINGS", mapper.findAllForExport(query).get(0).getPrepayBindingIssue());
        }
    }

    @Test
    void shouldKeepPaymentListReadableWhenExactPrepayBindingIsMissing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id = 'pay-submitted' "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setPaymentOrderId("PAY-SUBMITTED");

            assertEquals(1L, mapper.count(query));
            assertEquals(1, mapper.findAll(query).size());
            assertNull(mapper.findAll(query).get(0).getPayScene());
            assertEquals("PREPAY_BINDING_MISSING", mapper.findAll(query).get(0).getPrepayBindingIssue());
            assertEquals(1, mapper.findAllForExport(query).size());
            assertEquals("PREPAY_BINDING_MISSING", mapper.findAllForExport(query).get(0).getPrepayBindingIssue());
        }
    }

    @Test
    void shouldUseStableIdTieBreakerForPaymentListAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "status, status_type, created_at) VALUES "
                    + "('PAY-STABLE-001', 'ORD-STABLE', '客户一', 1.00, '微信支付', 'wx_h5', "
                    + "'WAIT_CALLBACK', 'warn', '2026-09-12 12:00:00'), "
                    + "('PAY-STABLE-002', 'ORD-STABLE', '客户二', 2.00, '微信支付', 'wx_h5', "
                    + "'WAIT_CALLBACK', 'warn', '2026-09-12 12:00:00'), "
                    + "('PAY-STABLE-003', 'ORD-STABLE', '客户三', 3.00, '微信支付', 'wx_h5', "
                    + "'WAIT_CALLBACK', 'warn', '2026-09-12 12:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setOrderNo("ORD-STABLE");
            query.setPageSize(1);

            assertEquals("PAY-STABLE-003", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(2);
            assertEquals("PAY-STABLE-002", mapper.findAll(query).get(0).getPaymentOrderId());
            query.setPageNo(3);
            assertEquals("PAY-STABLE-001", mapper.findAll(query).get(0).getPaymentOrderId());

            List<PaymentListItemDTO> exportItems = mapper.findAllForExport(query);
            assertEquals(3, exportItems.size());
            assertEquals("PAY-STABLE-003", exportItems.get(0).getPaymentOrderId());
            assertEquals("PAY-STABLE-002", exportItems.get(1).getPaymentOrderId());
            assertEquals("PAY-STABLE-001", exportItems.get(2).getPaymentOrderId());
        }
    }

    @Test
    void shouldApplyChannelFilterToPaymentListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "status, status_type, created_at) VALUES "
                    + "('PAY-CHANNEL-WX', 'ORD-CHANNEL-FILTER', '客户一', 1.00, '微信支付', 'wx_h5', "
                    + "'WAIT_CALLBACK', 'warn', '2026-09-12 12:00:00'), "
                    + "('PAY-CHANNEL-ALI', 'ORD-CHANNEL-FILTER', '客户二', 2.00, '支付宝', 'alipay_h5', "
                    + "'WAIT_CALLBACK', 'warn', '2026-09-12 12:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setOrderNo("ORD-CHANNEL-FILTER");
            query.setChannelCode("wx_h5");

            List<PaymentListItemDTO> items = mapper.findAll(query);
            List<PaymentListItemDTO> exportItems = mapper.findAllForExport(query);

            assertEquals(1, items.size());
            assertEquals("PAY-CHANNEL-WX", items.get(0).getPaymentOrderId());
            assertEquals(1L, mapper.count(query));
            assertEquals(1, exportItems.size());
            assertEquals("PAY-CHANNEL-WX", exportItems.get(0).getPaymentOrderId());
        }
    }

    @Test
    void shouldApplyDayEndPendingStatusGroupToListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, status, status_type, created_at) VALUES "
                    + "('PAY-DAY-END-PREPAY', 'ORD-DAY-END', '客户一', 10.00, 'PREPAY_CREATED', 'info', NOW()), "
                    + "('PAY-DAY-END-WAIT', 'ORD-DAY-END', '客户二', 20.00, 'WAIT_CALLBACK', 'warn', NOW()), "
                    + "('PAY-DAY-END-REVIEW', 'ORD-DAY-END', '客户三', 30.00, 'RISK_REVIEW', 'warn', NOW()), "
                    + "('PAY-DAY-END-BLOCKED', 'ORD-DAY-END', '客户四', 40.00, 'RISK_BLOCKED', 'danger', NOW()), "
                    + "('PAY-DAY-END-SUCCESS', 'ORD-DAY-END', '客户五', 50.00, 'SUCCESS', 'success', NOW())");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setOrderNo("ORD-DAY-END");
            query.setStatus("DAY_END_PENDING");

            List<PaymentListItemDTO> items = mapper.findAll(query);

            assertEquals(3, items.size());
            assertEquals(3L, mapper.count(query));
            assertEquals(3, mapper.findAllForExport(query).size());
            org.junit.jupiter.api.Assertions.assertTrue(items.stream().allMatch(item ->
                    java.util.Arrays.asList("PREPAY_CREATED", "WAIT_CALLBACK", "RISK_REVIEW")
                            .contains(item.getStatus())));
        }
    }

    @Test
    void shouldFilterSuccessfulPaymentsWithoutSuccessTime() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setStatus("SUCCESS_TIME_MISSING");

            List<PaymentListItemDTO> items = mapper.findAll(query);

            assertEquals(1, items.size());
            assertEquals("PAY-FINAL-SUCCESS", items.get(0).getPaymentOrderId());
            assertEquals(1L, mapper.count(query));
            assertEquals(1, mapper.findAllForExport(query).size());
        }
    }

    @Test
    void shouldApplyPaymentSuccessFactMismatchToListCountAndExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, channel_code, "
                    + "channel_transaction_no, status, status_type, success_at, created_at) VALUES "
                    + "('PAY-FACT-NORMAL', 'ORD-FACT', '客户一', 10.00, 'wx_h5', 'WX-FACT-NORMAL', 'SUCCESS', 'success', '2026-09-12 10:00:00', '2026-09-12 09:00:00'), "
                    + "('PAY-FACT-NO-ATTEMPT', 'ORD-FACT', '客户二', 20.00, 'wx_h5', 'WX-FACT-NO-ATTEMPT', 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-STATUS', 'ORD-FACT', '客户三', 30.00, 'wx_h5', 'WX-FACT-STATUS', 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-NO-COMPLETION', 'ORD-FACT', '客户四', 40.00, 'wx_h5', 'WX-FACT-NO-COMPLETION', 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-TYPE', 'ORD-FACT', '客户五', 50.00, 'wx_h5', 'WX-FACT-TYPE', 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-CHANNEL', 'ORD-FACT', '客户六', 60.00, 'wx_h5', NULL, 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-PAYMENT-TYPE', 'ORD-FACT', '客户七', 70.00, 'wx_h5', 'WX-FACT-PAYMENT-TYPE', 'SUCCESS', 'warn', '2026-09-12 10:00:00', '2026-09-12 09:00:00'), "
                    + "('PAY-FACT-CREATED-ORDER', 'ORD-FACT', '客户八', 80.00, 'wx_h5', 'WX-FACT-CREATED-ORDER', 'SUCCESS', 'success', '2026-09-12 10:00:00', '2026-09-12 11:00:00'), "
                    + "('PAY-FACT-ATTEMPT-ORDER', 'ORD-FACT', '客户九', 90.00, 'wx_h5', 'WX-FACT-ATTEMPT-ORDER', 'SUCCESS', 'success', '2026-09-12 10:00:00', '2026-09-12 09:00:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-FACT-NORMAL', '成功', 'success', '2026-09-12 09:30:00', '2026-09-12 09:10:00'), "
                    + "('PAY-FACT-STATUS', '成功', 'success', NOW(), '2026-09-12 09:00:00'), "
                    + "('PAY-FACT-STATUS', '处理中', 'info', NULL, '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-NO-COMPLETION', '成功', 'success', NULL, '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-TYPE', '成功', 'danger', NOW(), '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-CHANNEL', '成功', 'success', NOW(), '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-PAYMENT-TYPE', '成功', 'success', '2026-09-12 09:30:00', '2026-09-12 09:10:00'), "
                    + "('PAY-FACT-CREATED-ORDER', '成功', 'success', '2026-09-12 09:30:00', '2026-09-12 09:10:00'), "
                    + "('PAY-FACT-ATTEMPT-ORDER', '成功', 'success', '2026-09-12 10:01:00', '2026-09-12 09:30:00')");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5' "
                    + "WHERE payment_order_id LIKE 'PAY-FACT-%'");
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='微信支付' "
                    + "WHERE payment_order_id LIKE 'PAY-FACT-%'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order "
                    + "WHERE payment_order_id LIKE 'PAY-FACT-%'");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setOrderNo("ORD-FACT");
            query.setStatus("SUCCESS_FACT_MISMATCH");

            List<PaymentListItemDTO> items = mapper.findAll(query);

            assertEquals(8, items.size());
            assertEquals(8L, mapper.count(query));
            assertEquals(8, mapper.findAllForExport(query).size());

            for (String method : new String[]{"支付宝", "微信支付 ", " "}) {
                statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='" + method + "' "
                        + "WHERE payment_order_id='PAY-FACT-NORMAL'");
                session.clearCache();
                assertEquals(9L, mapper.count(query));
                assertTrue(mapper.findAll(query).stream()
                        .anyMatch(item -> "PAY-FACT-NORMAL".equals(item.getPaymentOrderId())));
                assertEquals(9, mapper.findAllForExport(query).size());
            }
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='微信支付' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(8L, mapper.count(query));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            org.junit.jupiter.api.Assertions.assertTrue(mapper.findAll(query).stream()
                    .anyMatch(item -> "PAY-FACT-NORMAL".equals(item.getPaymentOrderId())));
            assertEquals(9L, mapper.count(query));
            assertEquals(9, mapper.findAllForExport(query).size());
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='WX_H5' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(9L, mapper.count(query));
            assertEquals(9, mapper.findAll(query).size());
            assertEquals(9, mapper.findAllForExport(query).size());
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5 ' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(9L, mapper.count(query));
            assertEquals(9, mapper.findAll(query).size());
            assertEquals(9, mapper.findAllForExport(query).size());
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx_h5' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(8L, mapper.count(query));
            org.junit.jupiter.api.Assertions.assertEquals(
                    new java.util.HashSet<String>(java.util.Arrays.asList(
                            "PAY-FACT-NO-ATTEMPT", "PAY-FACT-STATUS", "PAY-FACT-NO-COMPLETION",
                            "PAY-FACT-TYPE", "PAY-FACT-CHANNEL", "PAY-FACT-PAYMENT-TYPE",
                            "PAY-FACT-CREATED-ORDER", "PAY-FACT-ATTEMPT-ORDER")),
                    items.stream().map(PaymentListItemDTO::getPaymentOrderId)
                            .collect(java.util.stream.Collectors.toSet()));

            statement.executeUpdate("UPDATE t_payment_order SET success_at='2026-09-12 09:30:00.123' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at='2026-09-12 09:30:00.124' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            org.junit.jupiter.api.Assertions.assertTrue(mapper.findAll(query).stream()
                    .anyMatch(item -> "PAY-FACT-NORMAL".equals(item.getPaymentOrderId())));
            assertEquals(9L, mapper.count(query));
            assertEquals(9, mapper.findAllForExport(query).size());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at='2026-09-12 09:30:00.122' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(8L, mapper.count(query));
            assertEquals(8, mapper.findAll(query).size());
            assertEquals(8, mapper.findAllForExport(query).size());

            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-12 09:31:00' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(9L, mapper.count(query));
            assertEquals(9, mapper.findAll(query).size());
            assertEquals(9, mapper.findAllForExport(query).size());
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-12 08:59:00' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(9L, mapper.count(query));
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-12 09:10:00' "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(8L, mapper.count(query));

            for (String amount : new String[]{"0.00", "-5.00", "NULL"}) {
                statement.executeUpdate("UPDATE t_payment_order SET amount=" + amount
                        + " WHERE payment_order_id='PAY-FACT-NORMAL'");
                session.clearCache();
                assertEquals(9L, mapper.count(query));
                assertTrue(mapper.findAll(query).stream()
                        .anyMatch(item -> "PAY-FACT-NORMAL".equals(item.getPaymentOrderId())));
                assertEquals(9, mapper.findAllForExport(query).size());
            }
            statement.executeUpdate("UPDATE t_payment_order SET amount=10.00 "
                    + "WHERE payment_order_id='PAY-FACT-NORMAL'");
            session.clearCache();
            assertEquals(8L, mapper.count(query));
        }
    }

    @Test
    void shouldOnlyAllowManualCloseBeforeChannelSubmission() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-SUBMITTED"));
        }
    }

    @Test
    void shouldNotClosePaymentWithActiveSubmitTokenEvenWhenAttemptIsMissing() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("INSERT INTO t_payment_submit_concurrency_token "
                    + "(prepay_order_no, payment_order_id, source_app_id, token_status, "
                    + "holder_idempotency_key, holder_terminal, holder_client_ip, "
                    + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                    + "('PRE-UNSUBMITTED-LOCK', 'PAY-UNSUBMITTED', 'app', 'ACTIVE', "
                    + "'pending-key', 'APP', '127.0.0.1', NOW(), "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()), NOW(), NOW())");

            assertTrue(mapper.hasActiveSubmitConcurrencyToken("PAY-UNSUBMITTED"));
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
            assertEquals(0, mapper.markExpiredUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
            try (java.sql.ResultSet resultSet = statement.executeQuery(
                    "SELECT status FROM t_payment_order WHERE payment_order_id='PAY-UNSUBMITTED'")) {
                assertTrue(resultSet.next());
                assertEquals("PREPAY_CREATED", resultSet.getString(1));
            }
            statement.executeUpdate("UPDATE t_payment_submit_concurrency_token SET token_status='RELEASED' "
                    + "WHERE payment_order_id='PAY-UNSUBMITTED'");
            assertFalse(mapper.hasActiveSubmitConcurrencyToken("PAY-UNSUBMITTED"));
            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
        }
    }

    @Test
    void shouldNotLetActiveSubmitTokenStarveOtherExpiredPayments() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "previous failure", 0, 2, 3600);
            statement.executeUpdate("INSERT INTO t_payment_submit_concurrency_token "
                    + "(prepay_order_no, payment_order_id, source_app_id, token_status, "
                    + "holder_idempotency_key, holder_terminal, holder_client_ip, "
                    + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                    + "('PRE-OLDER-LOCK', 'PAY-OLDER', 'app', 'ACTIVE', "
                    + "'pending-key', 'APP', '127.0.0.1', NOW(), "
                    + "TIMESTAMPADD(MINUTE, -1, NOW()), NOW(), NOW())");

            assertEquals("PAY-LEGACY-BLANK", mapper.findExpiredPayments(1).get(0).getPaymentOrderId());
            assertEquals(2, mapper.findExpiredPayments(100).size());
            assertEquals(1, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(0, countExpiryCloseRetries(session));

            statement.executeUpdate("UPDATE t_payment_submit_concurrency_token SET token_status='RELEASED' "
                    + "WHERE payment_order_id='PAY-OLDER'");
            session.clearCache();
            assertEquals("PAY-OLDER", mapper.findExpiredPayments(1).get(0).getPaymentOrderId());
        }
    }

    @Test
    void shouldKeepExpiryCandidateAndRetryForCaseVariantTokenOwner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            mapper.recordExpiryCloseFailure("PAY-OLDER", "previous failure", 0, 2, 3600);
            statement.executeUpdate("INSERT INTO t_payment_submit_concurrency_token "
                    + "(prepay_order_no, payment_order_id, source_app_id, token_status, "
                    + "holder_idempotency_key, holder_terminal, holder_client_ip, "
                    + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                    + "('PRE-ALIAS-LOCK', 'pay-older', 'app', 'ACTIVE', "
                    + "'alias-key', 'APP', '127.0.0.1', NOW(), "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()), NOW(), NOW())");

            assertEquals("PAY-OLDER", mapper.findExpiredPayments(1).get(0).getPaymentOrderId());
            assertEquals(0, mapper.deleteStaleExpiryCloseRetries(100));
            assertEquals(1, countExpiryCloseRetries(session));
        }
    }

    @Test
    void shouldNotTreatCaseVariantSubmitTokenAsExactPaymentOwner() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            statement.executeUpdate("INSERT INTO t_payment_submit_concurrency_token "
                    + "(prepay_order_no, payment_order_id, source_app_id, token_status, "
                    + "holder_idempotency_key, holder_terminal, holder_client_ip, "
                    + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                    + "('PRE-ALIAS-LOCK', 'pay-unsubmitted', 'app', 'ACTIVE', "
                    + "'alias-key', 'APP', '127.0.0.1', NOW(), "
                    + "TIMESTAMPADD(MINUTE, 1, NOW()), NOW(), NOW())");

            assertFalse(mapper.hasActiveSubmitConcurrencyToken("PAY-UNSUBMITTED"));
            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
        }
    }

    @Test
    void shouldNotManuallyClosePaymentAfterPrepayBindingDrifts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            statement.executeUpdate("UPDATE t_prepay_order SET amount = 67.00 "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));

            statement.executeUpdate("UPDATE t_prepay_order SET amount = 68.00, order_no = 'ORD-OTHER' "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));

            statement.executeUpdate("UPDATE t_prepay_order SET order_no = 'ORD-1' "
                    + "WHERE payment_order_id = 'PAY-UNSUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('PAY-UNSUBMITTED', 'ORD-1', 68.00)");
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-UNSUBMITTED"));
            try (java.sql.ResultSet row = statement.executeQuery(
                    "SELECT status FROM t_payment_order WHERE payment_order_id = 'PAY-UNSUBMITTED'")) {
                assertTrue(row.next());
                assertEquals("PREPAY_CREATED", row.getString(1));
            }
        }
    }

    private int countExpiryCloseRetries(SqlSession session) throws Exception {
        try (Statement statement = session.getConnection().createStatement();
             java.sql.ResultSet resultSet = statement.executeQuery(
                     "SELECT COUNT(1) FROM t_payment_expiry_close_retry")) {
            resultSet.next();
            return resultSet.getInt(1);
        }
    }

    private void assertPaymentSuccessTimePresent(SqlSession session, String paymentOrderId) throws Exception {
        try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT success_at FROM t_payment_order WHERE payment_order_id = ?")) {
            statement.setString(1, paymentOrderId);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                org.junit.jupiter.api.Assertions.assertNotNull(resultSet.getTimestamp(1));
            }
        }
    }

    private void assertLatestPaymentAttemptCompleted(SqlSession session, String paymentOrderId) throws Exception {
        try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT attempt_status, completed_at FROM t_payment_attempt "
                        + "WHERE payment_order_id = ? ORDER BY created_at DESC, id DESC LIMIT 1")) {
            statement.setString(1, paymentOrderId);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("成功", resultSet.getString("attempt_status"));
                org.junit.jupiter.api.Assertions.assertNotNull(resultSet.getTimestamp("completed_at"));
            }
        }
    }

    private void assertEarlierPaymentAttemptUnchanged(SqlSession session, String paymentOrderId) throws Exception {
        try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT attempt_status, completed_at FROM t_payment_attempt "
                        + "WHERE payment_order_id = ? ORDER BY created_at ASC, id ASC LIMIT 1")) {
            statement.setString(1, paymentOrderId);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals("待回调", resultSet.getString("attempt_status"));
                org.junit.jupiter.api.Assertions.assertNull(resultSet.getTimestamp("completed_at"));
            }
        }
    }

    private void assertPaymentAttemptStatuses(SqlSession session, String paymentOrderId,
            String earlierStatus, String earlierStatusType, String latestStatus, String latestStatusType)
            throws Exception {
        try (java.sql.PreparedStatement statement = session.getConnection().prepareStatement(
                "SELECT attempt_status, attempt_status_type FROM t_payment_attempt "
                        + "WHERE payment_order_id = ? ORDER BY created_at ASC, id ASC")) {
            statement.setString(1, paymentOrderId);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals(earlierStatus, resultSet.getString("attempt_status"));
                assertEquals(earlierStatusType, resultSet.getString("attempt_status_type"));
                org.junit.jupiter.api.Assertions.assertTrue(resultSet.next());
                assertEquals(latestStatus, resultSet.getString("attempt_status"));
                assertEquals(latestStatusType, resultSet.getString("attempt_status_type"));
                org.junit.jupiter.api.Assertions.assertFalse(resultSet.next());
            }
        }
    }

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            Connection connection = session.getConnection();
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentConfigProtocolVersionMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_bill");
            statement.execute("DROP TABLE IF EXISTS t_payment_submit_concurrency_token");
            statement.execute("DROP TABLE IF EXISTS t_payment_expiry_close_retry");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_submit_concurrency_token ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), source_app_id VARCHAR_IGNORECASE(64), "
                    + "token_status VARCHAR(32), holder_idempotency_key VARCHAR(128), "
                    + "holder_terminal VARCHAR(32), holder_client_ip VARCHAR(64), "
                    + "release_reason VARCHAR(128), last_occupied_at DATETIME, released_at DATETIME, "
                    + "expire_at DATETIME, created_at DATETIME, updated_at DATETIME, "
                    + "UNIQUE(prepay_order_no, source_app_id))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR_IGNORECASE(64), customer_name VARCHAR(128), amount DECIMAL(18, 2), "
                    + "payment_method VARCHAR_IGNORECASE(32), channel_code VARCHAR_IGNORECASE(64), "
                    + "channel_transaction_no VARCHAR_IGNORECASE(128), status VARCHAR_IGNORECASE(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_expiry_close_retry ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "retry_count INT NOT NULL, retry_interval_seconds INT NOT NULL, "
                    + "next_retry_at DATETIME NOT NULL, last_error_message VARCHAR(512) NOT NULL, "
                    + "last_failed_at DATETIME NOT NULL, created_at DATETIME NOT NULL, "
                    + "updated_at DATETIME NOT NULL, UNIQUE(payment_order_id))");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR_IGNORECASE(64), "
                    + "bill_no VARCHAR(64), order_no VARCHAR_IGNORECASE(64), customer_name VARCHAR(128), "
                    + "amount DECIMAL(18, 2), pay_scene VARCHAR(64), cashier_title VARCHAR(128), "
                    + "cashier_status VARCHAR(32), cashier_status_type VARCHAR(32), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), source_kind VARCHAR(16) DEFAULT 'VIRTUAL', "
                    + "created_at DATETIME, expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, attempt_no VARCHAR_IGNORECASE(64), "
                    + "prepay_order_no VARCHAR(64), idempotency_key VARCHAR_IGNORECASE(128), "
                    + "request_fingerprint VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(32) DEFAULT '微信支付', "
                    + "gateway_code VARCHAR(64), response_payload VARCHAR(2048), "
                    + "ops_config_snapshot_version BIGINT, "
                    + "attempt_status VARCHAR(64), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event (payment_order_id VARCHAR_IGNORECASE(64), "
                    + "event_type VARCHAR_IGNORECASE(64), biz_no VARCHAR_IGNORECASE(64), "
                    + "downstream_system VARCHAR_IGNORECASE(256), event_topic VARCHAR_IGNORECASE(128))");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, status, status_type) "
                    + "VALUES ('PAY-UNSUBMITTED', 'ORD-1', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-OLDER', 'ORD-0', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-SUBMITTED', 'ORD-2', 'WAIT_CALLBACK', 'warn'), "
                    + "('PAY-MANUAL-CLOSED', 'ORD-3', 'CLOSED', 'danger'), "
                    + "('PAY-EXPIRED-CLOSED', 'ORD-4', 'CLOSED', 'danger'), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', 'ORD-5', 'CLOSED', 'danger'), "
                    + "('PAY-MANUAL-CLOSEABLE', 'ORD-6', 'RISK_REVIEW', 'warn'), "
                    + "('PAY-FINAL-SUCCESS', 'ORD-7', 'SUCCESS', 'success'), "
                    + "('PAY-RISK-REJECTED', 'ORD-8', 'RISK_REJECTED', 'danger'), "
                    + "('PAY-ABNORMAL-WAIT', 'ORD-9', 'WAIT_CALLBACK', 'warn')");
            statement.executeUpdate("UPDATE t_payment_order "
                    + "SET channel_code='wx_h5', channel_transaction_no = 'WX-SUCCESS' "
                    + "WHERE payment_order_id = 'PAY-FINAL-SUCCESS'");
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='微信支付', channel_code='wx_h5' "
                    + "WHERE payment_order_id IN ('PAY-SUBMITTED', 'PAY-LEGACY-SUBMITTED-CLOSED')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, channel_transaction_no, status, status_type) "
                    + "VALUES ('PAY-LEGACY-BLANK', 'ORD-LEGACY', '', 'PREPAY_CREATED', 'info')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES "
                    + "('PAY-UNSUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW())), "
                    + "('PAY-OLDER', TIMESTAMPADD(MINUTE, -10, NOW())), "
                    + "('PAY-LEGACY-BLANK', TIMESTAMPADD(MINUTE, -7, NOW())), "
                    + "('PAY-SUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW()))");
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00 "
                    + "WHERE payment_order_id IN ('PAY-UNSUBMITTED', 'PAY-OLDER', 'PAY-LEGACY-BLANK')");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 68.00, order_no = "
                    + "CASE payment_order_id WHEN 'PAY-OLDER' THEN 'ORD-0' "
                    + "WHEN 'PAY-UNSUBMITTED' THEN 'ORD-1' ELSE 'ORD-LEGACY' END "
                    + "WHERE payment_order_id IN ('PAY-UNSUBMITTED', 'PAY-OLDER', 'PAY-LEGACY-BLANK')");
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-MANUAL-CLOSEABLE'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('PAY-MANUAL-CLOSEABLE', 'ORD-6', 68.00)");
            statement.executeUpdate("UPDATE t_payment_order SET amount = 68.00 "
                    + "WHERE payment_order_id IN ('PAY-SUBMITTED', 'PAY-LEGACY-SUBMITTED-CLOSED')");
            statement.executeUpdate("UPDATE t_prepay_order SET amount = 68.00 "
                    + "WHERE payment_order_id = 'PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "VALUES ('PAY-LEGACY-SUBMITTED-CLOSED', 'ORD-5', 68.00)");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no = 'PRE-SUBMITTED', "
                    + "order_no = 'ORD-2', cashier_status = '支付中', cashier_status_type = 'warn', "
                    + "created_at = NOW() WHERE payment_order_id = 'PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "attempt_no, payment_order_id, channel_code, attempt_status, attempt_status_type, created_at) VALUES "
                    + "('ATT-SUBMITTED-EARLIER', 'PAY-SUBMITTED', 'wx_h5', '待回调', 'warn', "
                    + "TIMESTAMPADD(MINUTE, -1, NOW())), "
                    + "('ATT-SUBMITTED-LATEST', 'PAY-SUBMITTED', 'wx_h5', '处理中', 'warn', NOW()), "
                    + "('ATT-LEGACY-CLOSED', 'PAY-LEGACY-SUBMITTED-CLOSED', 'wx_h5', '处理中', 'warn', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "attempt_no, payment_order_id, channel_code, attempt_status, attempt_status_type, "
                    + "completed_at, created_at) VALUES "
                    + "('ATT-FINAL-SUCCESS', 'PAY-FINAL-SUCCESS', 'wx_h5', '成功', 'success', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_payment_event"
                    + "(payment_order_id, event_type, biz_no, downstream_system, event_topic) "
                    + "VALUES ('PAY-EXPIRED-CLOSED', 'PAYMENT_EXPIRED_CLOSED', 'ORD-4', "
                    + "'payment-core-ops', 'payment.trade.expired-closed.v1'), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', 'PAYMENT_CLOSED', 'ORD-5', "
                    + "'accounting-system', 'payment.trade.closed.v1')");
            connection.commit();
        }
    }
}
