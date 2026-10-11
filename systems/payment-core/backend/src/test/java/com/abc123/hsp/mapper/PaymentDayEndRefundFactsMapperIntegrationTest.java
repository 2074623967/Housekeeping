package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.InputStream;
import java.math.BigDecimal;
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
 * 日终退款成功与完整性事实的 MySQL 兼容 SQL 集成测试。
 */
class PaymentDayEndRefundFactsMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_day_end_refund_facts;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
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
    void shouldAssignSuccessfulRefundsBySuccessDateAndMissingFactsByApplicationDate() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(Integer.valueOf(2), mapper.countSuccessRefundsByDate("2026-09-12"));
            assertEquals(new BigDecimal("30.00"), mapper.sumSuccessRefundAmountByDate("2026-09-12"));
            assertEquals(Integer.valueOf(0), mapper.countSuccessRefundsByDate("2026-09-11"));
            assertEquals(Integer.valueOf(1), mapper.countFailedRefundsByDate("2026-09-11"));
            assertEquals(new BigDecimal("50.00"), mapper.sumFailedRefundAmountByDate("2026-09-11"));
            assertEquals(Integer.valueOf(1), mapper.countFailedRefundsByDate("2026-09-12"));
            assertEquals(new BigDecimal("60.00"), mapper.sumFailedRefundAmountByDate("2026-09-12"));
            assertEquals(Integer.valueOf(1), mapper.countMissingRefundSuccessTimeByDate("2026-09-11"));
            assertEquals(Integer.valueOf(0), mapper.countMissingRefundSuccessTimeByDate("2026-09-12"));
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    @Test
    void shouldFlagSuccessAttemptCompletedAfterSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_refund_attempt SET completed_at = '2026-09-12 00:00:00.001' "
                    + "WHERE refund_order_id = 'REF-001'");
            assertEquals(Integer.valueOf(2), session.getMapper(PaymentDayEndMapper.class)
                    .countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    @Test
    void shouldFlagNonPositiveTerminalRefundAmounts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            for (String amount : new String[] {"0.00", "-5.00"}) {
                statement.executeUpdate("UPDATE t_refund_order SET refund_amount = " + amount
                        + " WHERE refund_order_id = 'REF-001'");
                session.clearCache();
                assertEquals(Integer.valueOf(2), session.getMapper(PaymentDayEndMapper.class)
                        .countRefundTerminalFactMismatchByDate("2026-09-12"));
            }
        }
    }

    @Test
    void shouldFlagRefundExceedingOriginalPaidAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-UNDERPAID', 5.00)");
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'PAY-UNDERPAID' "
                    + "WHERE refund_order_id = 'REF-001'");
            assertEquals(Integer.valueOf(2), session.getMapper(PaymentDayEndMapper.class)
                    .countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    @Test
    void shouldFlagAllIndividuallyValidSuccessesWhenTheirTotalExceedsPaidAmount() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-OVERBOOKED', 25.00)");
            statement.executeUpdate("UPDATE t_refund_order SET payment_order_id = 'PAY-OVERBOOKED' "
                    + "WHERE refund_order_id IN ('REF-001', 'REF-002')");
            assertEquals(Integer.valueOf(3), session.getMapper(PaymentDayEndMapper.class)
                    .countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    @Test
    void shouldRequireExactTerminalStatusButAllowCaseVariantsOfStatusType() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_order SET status_type='SUCCESS' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type='SUCCESS' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_order SET status='success' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='success' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_order SET status='SUCCESS' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='SUCCESS' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='SUCCESS ' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='SUCCESS' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_order SET status_type='success ' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));
            statement.executeUpdate("UPDATE t_refund_order SET status_type='success' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type='success ' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    @Test
    void shouldRequireExactFailedStatusAndAttemptWithCaseInsensitiveTypes() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            statement.executeUpdate("UPDATE t_refund_order SET success_at=NULL, status_type='DANGER' "
                    + "WHERE refund_order_id='REF-005'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type='DANGER' "
                    + "WHERE refund_order_id='REF-005'");
            assertEquals(Integer.valueOf(0), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_refund_order SET status='fail' WHERE refund_order_id='REF-005'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_refund_order SET status='FAIL' WHERE refund_order_id='REF-005'");
            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='fail' "
                    + "WHERE refund_order_id='REF-005'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status='FAIL', "
                    + "attempt_status_type='DANGER ' WHERE refund_order_id='REF-005'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type='danger' "
                    + "WHERE refund_order_id='REF-005'");
            statement.executeUpdate("UPDATE t_refund_order SET status_type='danger ' "
                    + "WHERE refund_order_id='REF-005'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-11"));
        }
    }

    @Test
    void shouldRejectCaseVariantAndBlankRefundChannelBindings() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            assertEquals(Integer.valueOf(1), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='WX_H5' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='wx_h5', channel_refund_no='ch-001' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_attempt SET channel_refund_no='CH-001' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_order SET channel_code='   ' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='   ' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));

            statement.executeUpdate("UPDATE t_refund_order SET channel_code='wx_h5', channel_refund_no=' ' "
                    + "WHERE refund_order_id='REF-001'");
            statement.executeUpdate("UPDATE t_refund_attempt SET channel_code='wx_h5', channel_refund_no=' ' "
                    + "WHERE refund_order_id='REF-001'");
            session.clearCache();
            assertEquals(Integer.valueOf(2), mapper.countRefundTerminalFactMismatchByDate("2026-09-12"));
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("CREATE TABLE t_payment_order (payment_order_id VARCHAR(64) PRIMARY KEY, "
                    + "amount DECIMAL(18, 2) NOT NULL)");
            statement.executeUpdate("INSERT INTO t_payment_order VALUES ('PAY-REFUND-FACTS', 100.00)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "payment_order_id VARCHAR(64) NOT NULL DEFAULT 'PAY-REFUND-FACTS', "
                    + "refund_amount DECIMAL(18, 2) NOT NULL, "
                    + "status VARCHAR_IGNORECASE(32) NOT NULL, status_type VARCHAR_IGNORECASE(32), applied_at DATETIME NOT NULL, "
                    + "channel_code VARCHAR_IGNORECASE(64), channel_refund_no VARCHAR_IGNORECASE(128), channel_submitted_at DATETIME, "
                    + "success_at DATETIME, terminal_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64) NOT NULL, "
                    + "channel_code VARCHAR_IGNORECASE(64), channel_refund_no VARCHAR_IGNORECASE(128), "
                    + "attempt_status VARCHAR_IGNORECASE(32) NOT NULL, attempt_status_type VARCHAR_IGNORECASE(32), "
                    + "completed_at DATETIME, created_at DATETIME NOT NULL)");
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, refund_amount, status, status_type, applied_at, channel_code, channel_refund_no, "
                    + "channel_submitted_at, success_at, terminal_at) VALUES "
                    + "('REF-001', 10.00, 'SUCCESS', 'success', '2026-09-11 10:00:00', 'wx_h5', 'CH-001', '2026-09-11 10:01:00', "
                    + "'2026-09-12 00:00:00', '2026-09-12 09:00:00'), "
                    + "('REF-002', 20.00, 'SUCCESS', 'success', '2026-09-12 00:00:00', 'wx_h5', 'CH-002', '2026-09-12 10:00:00', "
                    + "'2026-09-12 11:00:00', '2026-09-12 11:00:00'), "
                    + "('REF-003', 30.00, 'SUCCESS', 'success', '2026-09-11 00:00:00', 'wx_h5', 'CH-003', '2026-09-11 12:00:00', NULL, '2026-09-11 13:00:00'), "
                    + "('REF-004', 40.00, 'PROCESSING', NULL, '2026-09-11 13:00:00', NULL, NULL, NULL, NULL, NULL), "
                    + "('REF-005', 50.00, 'FAIL', 'danger', '2026-09-11 14:00:00', 'wx_h5', 'CH-005', '2026-09-11 14:01:00', "
                    + "'2026-09-11 15:00:00', '2026-09-11 15:00:00'), "
                    + "('REF-006', 60.00, 'FAIL', 'danger', '2026-09-11 16:00:00', 'wx_h5', 'CH-006', '2026-11-11 16:01:00', NULL, '2026-09-12 00:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-001', 'wx_h5', 'CH-001', 'SUCCESS', 'success', '2026-09-12 00:00:00', '2026-09-11 10:01:00'), "
                    + "('REF-002', 'wx_h5', 'CH-002', 'SUCCESS', 'success', '2026-09-12 11:00:00', '2026-09-12 10:01:00'), "
                    + "('REF-003', 'wx_h5', 'CH-003', 'SUCCESS', 'success', '2026-09-11 13:00:00', '2026-09-11 12:01:00'), "
                    + "('REF-005', 'wx_h5', 'CH-005', 'FAIL', 'danger', '2026-09-11 15:00:00', '2026-09-11 14:01:00')");
            session.commit();
        }
    }
}
