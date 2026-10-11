package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
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
 * 日终支付成功事实一致性的 MySQL 兼容 SQL 集成测试。
 */
class PaymentDayEndPaymentSuccessFactsMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_day_end_success_facts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentDayEndMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentDayEndMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentDayEndMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldCountSuccessFactMismatchesBySuccessDate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            assertEquals(Integer.valueOf(0),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-12"));
            assertEquals(Integer.valueOf(0),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-10"));
            assertEquals(Integer.valueOf(1),
                    mapper.countMissingPaymentSuccessTimeByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_order SET success_at='2026-09-11 10:00:00.123' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at='2026-09-11 10:00:00.124' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at='2026-09-11 10:00:00.122' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-a' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='CHANNEL-A' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-a ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-a' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-11 10:00:00.123' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-11 08:59:00' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET created_at='2026-09-11 09:30:00' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            for (String amount : new String[]{"0.00", "-5.00", "NULL"}) {
                statement.executeUpdate("UPDATE t_payment_order SET amount=" + amount
                        + " WHERE payment_order_id='PAY-NORMAL'");
                session.clearCache();
                assertEquals(Integer.valueOf(9),
                        mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            }
            statement.executeUpdate("UPDATE t_payment_order SET amount=1.00 "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8),
                    mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldCountPaymentMethodDriftAsSuccessFactMismatch() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            for (String method : new String[]{"支付宝", "微信支付 ", " "}) {
                statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='" + method + "' "
                        + "WHERE payment_order_id='PAY-NORMAL'");
                session.clearCache();
                assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            }
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='ALIPAY' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='alipay' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_order SET payment_method=' ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method=' ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='微信支付' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='微信支付' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldCountUnknownAndDriftedPaymentSourcesBySuccessDate() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='virtual' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0)) "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='UNKNOWN' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='STANDARD_ORDER' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("INSERT INTO t_order(order_no) VALUES ('PAY-NORMAL')");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("DELETE FROM t_order WHERE order_no='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount, source_kind) "
                    + "VALUES ('PAY-NORMAL', 'PAY-NORMAL', 1.00, 'VIRTUAL')");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldNotTrustAliasedPrepayOrAttemptPaymentOrderId() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='pay-normal' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-NORMAL' "
                    + "WHERE payment_order_id='pay-normal'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-NORMAL ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET payment_order_id='PAY-NORMAL' "
                    + "WHERE payment_order_id='PAY-NORMAL '");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id='pay-normal' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id='PAY-NORMAL' "
                    + "WHERE payment_order_id='pay-normal'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id='PAY-NORMAL ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_order_id='PAY-NORMAL' "
                    + "WHERE payment_order_id='PAY-NORMAL '");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldAcceptCaseVariantsButRejectPaddedSuccessFacts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_order SET status_type='SUCCESS' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status_type='SUCCESS' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_order SET status_type='success ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_payment_order SET status_type='success' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status_type='success ' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldRejectCaseVariantPrepayAndOriginalOrderNumbers() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(8), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
            statement.executeUpdate("UPDATE t_prepay_order SET order_no='pay-normal' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_prepay_order SET order_no='PAY-NORMAL', source_kind='STANDARD_ORDER' "
                    + "WHERE payment_order_id='PAY-NORMAL'");
            statement.executeUpdate("INSERT INTO t_order VALUES ('pay-normal')");
            session.clearCache();
            assertEquals(Integer.valueOf(9), mapper.countPaymentSuccessFactMismatchByDate("2026-09-11"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "order_no VARCHAR_IGNORECASE(64), "
                    + "amount DECIMAL(18,2) DEFAULT 1.00, "
                    + "channel_code VARCHAR_IGNORECASE(64), payment_method VARCHAR_IGNORECASE(32), "
                    + "channel_transaction_no VARCHAR(128), status VARCHAR(32) NOT NULL, "
                    + "status_type VARCHAR(32) DEFAULT 'success', "
                    + "success_at DATETIME, created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64) NOT NULL, "
                    + "channel_code VARCHAR_IGNORECASE(64), payment_method VARCHAR_IGNORECASE(32), "
                    + "attempt_status VARCHAR(64), attempt_status_type VARCHAR(32), completed_at DATETIME, "
                    + "created_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR_IGNORECASE(64), amount DECIMAL(18,2), source_kind VARCHAR_IGNORECASE(16))");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR_IGNORECASE(64))");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, channel_code, channel_transaction_no, status, success_at, created_at) VALUES "
                    + "('PAY-NORMAL', 'channel-a', 'TX-NORMAL', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-NO-ATTEMPT', 'channel-a', 'TX-NO-ATTEMPT', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 00:00:00'), "
                    + "('PAY-STATUS-MISMATCH', 'channel-a', 'TX-STATUS-MISMATCH', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-NO-COMPLETION', 'channel-a', 'TX-NO-COMPLETION', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-CROSS-DAY-MISMATCH', 'channel-a', 'TX-CROSS-DAY-MISMATCH', 'SUCCESS', '2026-09-11 00:00:00', '2026-09-10 23:00:00'), "
                    + "('PAY-MISSING-CHANNEL-TRANSACTION', 'channel-a', NULL, 'SUCCESS', '2026-09-11 11:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-PAYMENT-TYPE-MISMATCH', 'channel-a', 'TX-PAYMENT-TYPE-MISMATCH', 'SUCCESS', '2026-09-11 12:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-CREATED-AFTER-SUCCESS', 'channel-a', 'TX-CREATED-AFTER-SUCCESS', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 11:00:00'), "
                    + "('PAY-ATTEMPT-AFTER-SUCCESS', 'channel-a', 'TX-ATTEMPT-AFTER-SUCCESS', 'SUCCESS', '2026-09-11 10:00:00', '2026-09-11 09:00:00'), "
                    + "('PAY-TIME-ONLY', 'channel-a', 'TX-TIME-ONLY', 'SUCCESS', NULL, '2026-09-11 09:00:00'), "
                    + "('PAY-CLOSED', 'channel-a', NULL, 'CLOSED', NULL, '2026-09-11 09:00:00'), "
                    + "('PAY-NEXT-DAY', 'channel-a', 'TX-NEXT-DAY', 'SUCCESS', '2026-09-12 10:00:00', '2026-09-12 00:00:00')");
            statement.executeUpdate("UPDATE t_payment_order SET status_type = 'warn' "
                    + "WHERE payment_order_id = 'PAY-PAYMENT-TYPE-MISMATCH'");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-NORMAL', '成功', 'success', '2026-09-11 10:00:00', '2026-09-11 09:30:00'), "
                    + "('PAY-STATUS-MISMATCH', '成功', 'success', '2026-09-11 09:30:00', '2026-09-11 09:20:00'), "
                    + "('PAY-STATUS-MISMATCH', '处理中', 'processing', NULL, '2026-09-11 09:40:00'), "
                    + "('PAY-NO-COMPLETION', '成功', 'success', NULL, '2026-09-11 09:30:00'), "
                    + "('PAY-MISSING-CHANNEL-TRANSACTION', '成功', 'success', '2026-09-11 11:00:00', '2026-09-11 10:30:00'), "
                    + "('PAY-PAYMENT-TYPE-MISMATCH', '成功', 'danger', '2026-09-11 11:30:00', '2026-09-11 11:00:00'), "
                    + "('PAY-CREATED-AFTER-SUCCESS', '成功', 'success', '2026-09-11 09:30:00', '2026-09-11 09:00:00'), "
                    + "('PAY-ATTEMPT-AFTER-SUCCESS', '成功', 'success', '2026-09-11 10:01:00', '2026-09-11 09:30:00'), "
                    + "('PAY-TIME-ONLY', '成功', 'success', '2026-09-11 10:00:00', '2026-09-11 09:30:00'), "
                    + "('PAY-NEXT-DAY', '成功', 'success', '2026-09-12 10:00:00', '2026-09-12 09:30:00')");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-a'");
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='微信支付'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='微信支付'");
            statement.executeUpdate("UPDATE t_payment_order SET order_no=payment_order_id");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount, source_kind) "
                    + "SELECT payment_order_id, order_no, amount, 'VIRTUAL' FROM t_payment_order");
            session.commit();
        }
    }
}
