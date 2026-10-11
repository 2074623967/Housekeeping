package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.RefundPaymentSourceDTO;
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

class RefundPaymentSourceMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:refund_payment_source;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa", "");
        Configuration configuration = new Configuration(
                new Environment("test", new JdbcTransactionFactory(), dataSource));
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.addMapper(RefundMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_payment_order (payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR_IGNORECASE(64), customer_name VARCHAR(64), amount DECIMAL(18,2), "
                    + "status VARCHAR_IGNORECASE(32), status_type VARCHAR_IGNORECASE(32), payment_method VARCHAR(32), "
                    + "channel_code VARCHAR(32), channel_transaction_no VARCHAR(64), "
                    + "created_at DATETIME, success_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR_IGNORECASE(64), "
                    + "order_no VARCHAR_IGNORECASE(64), amount DECIMAL(18,2), source_kind VARCHAR_IGNORECASE(16))");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR_IGNORECASE(64))");
            statement.execute("CREATE TABLE t_payment_attempt (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR_IGNORECASE(64), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR_IGNORECASE(32), channel_code VARCHAR(32), "
                    + "payment_method VARCHAR_IGNORECASE(32), "
                    + "created_at DATETIME, completed_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES "
                    + "('PAY-001', 'ORD-001', '客户', 168.00, 'SUCCESS', 'success', "
                    + "'微信支付', 'wx_h5', 'WX-001', '2026-10-02 09:00:00', '2026-10-02 09:02:00')");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, "
                    + "attempt_status_type, channel_code, payment_method, created_at, completed_at) VALUES "
                    + "('PAY-001', '成功', 'success', 'wx_h5', '微信支付', "
                    + "'2026-10-02 09:01:00', '2026-10-02 09:02:00')");
            statement.executeUpdate("INSERT INTO t_prepay_order VALUES "
                    + "('PAY-001', 'ORD-001', 168.00, 'VIRTUAL')");
            session.commit();
        }
    }

    @Test
    void shouldRequireUniqueAndCurrentSourceForRefundableSuccess() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_prepay_order SET source_kind='virtual'");
        assertFalse(validSource());
        update("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0))");
        assertFalse(validSource());
        update("UPDATE t_prepay_order SET source_kind='VIRTUAL'");
        update("UPDATE t_prepay_order SET source_kind='UNKNOWN'");
        assertFalse(validSource());
        update("UPDATE t_prepay_order SET source_kind='STANDARD_ORDER'");
        assertFalse(validSource());
        update("INSERT INTO t_order VALUES ('ORD-001')");
        assertTrue(validSource());
        update("UPDATE t_prepay_order SET source_kind='VIRTUAL'");
        assertFalse(validSource());
        update("DELETE FROM t_order");
        assertTrue(validSource());
        update("INSERT INTO t_prepay_order VALUES ('PAY-001', 'ORD-001', 168.00, 'VIRTUAL')");
        assertFalse(validSource());
    }

    @Test
    void shouldRejectPaddedSuccessStatusesEvenUnderInsensitiveComparison() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_payment_order SET status='SUCCESS '");
        assertFalse(validSource());
        update("UPDATE t_payment_order SET status='SUCCESS'");
        update("UPDATE t_payment_order SET status_type='success '");
        assertFalse(validSource());
        update("UPDATE t_payment_order SET status_type='SUCCESS'");
        assertTrue(validSource());
        update("UPDATE t_payment_attempt SET attempt_status_type='success '");
        assertFalse(validSource());
        update("UPDATE t_payment_attempt SET attempt_status_type='SUCCESS'");
        assertTrue(validSource());
    }

    @Test
    void shouldRejectCaseVariantPrepayAndOriginalOrderNumbers() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_prepay_order SET order_no='ord-001'");
        assertFalse(validSource());
        update("UPDATE t_prepay_order SET order_no='ORD-001', source_kind='STANDARD_ORDER'");
        update("INSERT INTO t_order VALUES ('ord-001')");
        assertFalse(validSource());
    }

    @Test
    void shouldNotLockPaymentSourceThroughNumberAlias() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            assertNull(session.getMapper(RefundMapper.class).findPaymentSourceForUpdate("pay-001"));
        }
    }

    @Test
    void shouldRejectRefundSourceWhenLatestSuccessAttemptPaymentMethodDrifts() throws Exception {
        assertTrue(validSource());
        for (String method : new String[]{"支付宝", "微信支付 ", " "}) {
            update("UPDATE t_payment_attempt SET payment_method='" + method + "'");
            assertFalse(validSource());
        }
        update("UPDATE t_payment_order SET payment_method='ALIPAY'");
        update("UPDATE t_payment_attempt SET payment_method='alipay'");
        assertFalse(validSource());
        update("UPDATE t_payment_order SET payment_method=' '");
        update("UPDATE t_payment_attempt SET payment_method=' '");
        assertFalse(validSource());
        update("UPDATE t_payment_order SET payment_method='微信支付'");
        update("UPDATE t_payment_attempt SET payment_method='微信支付'");
        assertTrue(validSource());
    }

    @Test
    void shouldRejectRefundSourceWhenLatestSuccessAttemptChannelIsOnlyAnAlias() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_payment_attempt SET channel_code='wx_h5 '");
        assertFalse(validSource());
        update("UPDATE t_payment_attempt SET channel_code='wx_h5'");
        assertTrue(validSource());
    }

    @Test
    void shouldRejectRefundSourceWhenSuccessAttemptTimelineIsInverted() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_payment_attempt SET created_at='2026-10-02 08:59:59'");
        assertFalse(validSource());
        update("UPDATE t_payment_attempt SET created_at='2026-10-02 09:02:01'");
        assertFalse(validSource());
        update("UPDATE t_payment_attempt SET created_at='2026-10-02 09:01:00'");
        assertTrue(validSource());
    }

    @Test
    void shouldNotUsePrepayFromPaymentNumberAlias() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_prepay_order SET payment_order_id='pay-001'");
        assertFalse(validSource());
    }

    @Test
    void shouldNotUseAttemptFromPaymentNumberAlias() throws Exception {
        assertTrue(validSource());
        update("UPDATE t_payment_attempt SET payment_order_id='pay-001'");
        assertFalse(validSource());
    }

    @Test
    void shouldNotCountAliasPrepayAsOwningSource() throws Exception {
        assertTrue(validSource());
        update("INSERT INTO t_prepay_order VALUES ('pay-001', 'ORD-001', 168.00, 'VIRTUAL')");
        assertTrue(validSource());
    }

    @Test
    void shouldNotLetAliasAttemptDisplaceLatestOwningAttempt() throws Exception {
        assertTrue(validSource());
        update("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, attempt_status_type, "
                + "channel_code, created_at, completed_at) VALUES "
                + "('pay-001', '处理中', 'warning', 'wx_h5', "
                + "'2026-10-02 09:01:30', '2026-10-02 09:02:00')");
        assertTrue(validSource());
    }

    private boolean validSource() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            RefundPaymentSourceDTO source = session.getMapper(RefundMapper.class)
                    .findPaymentSourceForUpdate("PAY-001");
            assertEquals("PAY-001", source.getPaymentOrderId());
            return Boolean.TRUE.equals(source.getPaymentSuccessFactsValid());
        }
    }

    private void update(String sql) throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate(sql);
            session.commit();
        }
    }
}
