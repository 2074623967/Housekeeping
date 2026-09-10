package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.sql.Connection;
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
    void shouldOnlyAllowManualCloseBeforeChannelSubmission() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMapper mapper = session.getMapper(PaymentMapper.class);

            assertEquals(1, mapper.markUnsubmittedPaymentClosed("PAY-MANUAL-CLOSEABLE"));
            assertEquals(0, mapper.markUnsubmittedPaymentClosed("PAY-SUBMITTED"));
        }
    }

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            Connection connection = session.getConnection();
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
                    + "status_type VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_expiry_close_retry ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64) NOT NULL, "
                    + "retry_count INT NOT NULL, retry_interval_seconds INT NOT NULL, "
                    + "next_retry_at DATETIME NOT NULL, last_error_message VARCHAR(512) NOT NULL, "
                    + "last_failed_at DATETIME NOT NULL, created_at DATETIME NOT NULL, "
                    + "updated_at DATETIME NOT NULL, UNIQUE(payment_order_id))");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR(64), expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt (payment_order_id VARCHAR(64))");
            statement.execute("CREATE TABLE t_payment_event (payment_order_id VARCHAR(64), event_type VARCHAR(64))");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, status, status_type) "
                    + "VALUES ('PAY-UNSUBMITTED', 'ORD-1', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-OLDER', 'ORD-0', 'PREPAY_CREATED', 'info'), "
                    + "('PAY-SUBMITTED', 'ORD-2', 'WAIT_CALLBACK', 'warn'), "
                    + "('PAY-MANUAL-CLOSED', 'ORD-3', 'CLOSED', 'danger'), "
                    + "('PAY-EXPIRED-CLOSED', 'ORD-4', 'CLOSED', 'danger'), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', 'ORD-5', 'CLOSED', 'danger'), "
                    + "('PAY-MANUAL-CLOSEABLE', 'ORD-6', 'RISK_REVIEW', 'warn')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, channel_transaction_no, status, status_type) "
                    + "VALUES ('PAY-LEGACY-BLANK', 'ORD-LEGACY', '', 'PREPAY_CREATED', 'info')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES "
                    + "('PAY-UNSUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW())), "
                    + "('PAY-OLDER', TIMESTAMPADD(MINUTE, -10, NOW())), "
                    + "('PAY-LEGACY-BLANK', TIMESTAMPADD(MINUTE, -7, NOW())), "
                    + "('PAY-SUBMITTED', TIMESTAMPADD(MINUTE, -5, NOW()))");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) VALUES "
                    + "('PAY-SUBMITTED'), ('PAY-LEGACY-SUBMITTED-CLOSED')");
            statement.executeUpdate("INSERT INTO t_payment_event(payment_order_id, event_type) "
                    + "VALUES ('PAY-EXPIRED-CLOSED', 'PAYMENT_EXPIRED_CLOSED'), "
                    + "('PAY-LEGACY-SUBMITTED-CLOSED', 'PAYMENT_CLOSED')");
            connection.commit();
        }
    }
}
