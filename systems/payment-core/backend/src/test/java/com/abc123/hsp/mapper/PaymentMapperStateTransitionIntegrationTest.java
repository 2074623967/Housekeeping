package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentListItemDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
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
    void shouldUseStablePaymentOrderIdTieBreakerForExpiredPaymentBatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status = 'CLOSED' "
                    + "WHERE payment_order_id IN ('PAY-OLDER', 'PAY-LEGACY-BLANK', 'PAY-UNSUBMITTED')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, status, status_type) VALUES "
                    + "('PAY-EXPIRY-STABLE-003', 'ORD-EXPIRY-STABLE-003', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-EXPIRY-STABLE-001', 'ORD-EXPIRY-STABLE-001', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-EXPIRY-STABLE-002', 'ORD-EXPIRY-STABLE-002', 'PREPAY_CREATED', 'info')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES "
                    + "('PAY-EXPIRY-STABLE-003', TIMESTAMP '2026-09-12 00:00:00'), "
                    + "('PAY-EXPIRY-STABLE-001', TIMESTAMP '2026-09-12 00:00:00'), "
                    + "('PAY-EXPIRY-STABLE-002', TIMESTAMP '2026-09-12 00:00:00')");
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
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult("PAY-EXPIRED-CLOSED", "WX-EXPIRED"));
            assertEquals(1, mapper.markPaymentSuccessFromChannelResult(
                    "PAY-LEGACY-SUBMITTED-CLOSED", "WX-LEGACY-MANUAL"));

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT status FROM t_payment_order "
                                 + "WHERE payment_order_id = 'PAY-EXPIRED-CLOSED'")) {
                resultSet.next();
                assertEquals("SUCCESS", resultSet.getString(1));
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
                    "PAY-FINAL-SUCCESS", "WX-LATE-PENDING"));
            assertEquals(0, mapper.markPaymentPendingFromCallback(
                    "PAY-MANUAL-CLOSED", "WX-LATE-PENDING"));

            try (Statement statement = session.getConnection().createStatement();
                 java.sql.ResultSet resultSet = statement.executeQuery(
                         "SELECT status, channel_transaction_no FROM t_payment_order "
                                 + "WHERE payment_order_id = 'PAY-FINAL-SUCCESS'")) {
                resultSet.next();
                assertEquals("SUCCESS", resultSet.getString("status"));
                assertEquals("WX-SUCCESS", resultSet.getString("channel_transaction_no"));
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
            assertEquals("WAIT_CALLBACK", submissionFact.getStatus());
            assertEquals("处理中", submissionFact.getLatestAttemptStatus());
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
                    + "payment_order_id, order_no, customer_name, amount, status, status_type, success_at, created_at) VALUES "
                    + "('PAY-FACT-NORMAL', 'ORD-FACT', '客户一', 10.00, 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-NO-ATTEMPT', 'ORD-FACT', '客户二', 20.00, 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-STATUS', 'ORD-FACT', '客户三', 30.00, 'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-FACT-NO-COMPLETION', 'ORD-FACT', '客户四', 40.00, 'SUCCESS', 'success', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, completed_at, created_at) VALUES "
                    + "('PAY-FACT-NORMAL', '成功', NOW(), '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-STATUS', '成功', NOW(), '2026-09-12 09:00:00'), "
                    + "('PAY-FACT-STATUS', '处理中', NULL, '2026-09-12 10:00:00'), "
                    + "('PAY-FACT-NO-COMPLETION', '成功', NULL, '2026-09-12 10:00:00')");
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);
            PaymentListQueryDTO query = new PaymentListQueryDTO();
            query.setOrderNo("ORD-FACT");
            query.setStatus("SUCCESS_FACT_MISMATCH");

            List<PaymentListItemDTO> items = mapper.findAll(query);

            assertEquals(3, items.size());
            assertEquals(3L, mapper.count(query));
            assertEquals(3, mapper.findAllForExport(query).size());
            org.junit.jupiter.api.Assertions.assertEquals(
                    java.util.Set.of("PAY-FACT-NO-ATTEMPT", "PAY-FACT-STATUS", "PAY-FACT-NO-COMPLETION"),
                    items.stream().map(PaymentListItemDTO::getPaymentOrderId)
                            .collect(java.util.stream.Collectors.toSet()));
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
            statement.execute("DROP TABLE IF EXISTS t_payment_expiry_close_retry");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), customer_name VARCHAR(128), amount DECIMAL(18, 2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), status VARCHAR(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_expiry_close_retry ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "retry_count INT NOT NULL, retry_interval_seconds INT NOT NULL, "
                    + "next_retry_at DATETIME NOT NULL, last_error_message VARCHAR(512) NOT NULL, "
                    + "last_failed_at DATETIME NOT NULL, created_at DATETIME NOT NULL, "
                    + "updated_at DATETIME NOT NULL, UNIQUE(payment_order_id))");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, prepay_order_no VARCHAR(64), "
                    + "bill_no VARCHAR(64), order_no VARCHAR(64), customer_name VARCHAR(128), "
                    + "amount DECIMAL(18, 2), pay_scene VARCHAR(64), cashier_title VARCHAR(128), "
                    + "cashier_status VARCHAR(32), cashier_status_type VARCHAR(32), "
                    + "payment_order_id VARCHAR(64), created_at DATETIME, expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "attempt_status VARCHAR(64), attempt_status_type VARCHAR(32), "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event (payment_order_id VARCHAR(64), event_type VARCHAR(64))");
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
                    + "SET channel_transaction_no = 'WX-SUCCESS' "
                    + "WHERE payment_order_id = 'PAY-FINAL-SUCCESS'");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, channel_transaction_no, status, status_type) "
                    + "VALUES ('PAY-LEGACY-BLANK', 'ORD-LEGACY', '', 'PREPAY_CREATED', 'info')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES "
                    + "('PAY-UNSUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW())), "
                    + "('PAY-OLDER', TIMESTAMPADD(MINUTE, -10, NOW())), "
                    + "('PAY-LEGACY-BLANK', TIMESTAMPADD(MINUTE, -7, NOW())), "
                    + "('PAY-SUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW()))");
            statement.executeUpdate("UPDATE t_prepay_order SET prepay_order_no = 'PRE-SUBMITTED', "
                    + "order_no = 'ORD-2', cashier_status = '支付中', cashier_status_type = 'warn', "
                    + "created_at = NOW() WHERE payment_order_id = 'PAY-SUBMITTED'");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) VALUES "
                    + "('PAY-SUBMITTED', '待回调', TIMESTAMPADD(MINUTE, -1, NOW())), "
                    + "('PAY-SUBMITTED', '处理中', NOW()), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', '处理中', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, completed_at, created_at) "
                    + "VALUES ('PAY-FINAL-SUCCESS', '成功', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_payment_event(payment_order_id, event_type) "
                    + "VALUES ('PAY-EXPIRED-CLOSED', 'PAYMENT_EXPIRED_CLOSED'), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', 'PAYMENT_CLOSED')");
            connection.commit();
        }
    }
}
