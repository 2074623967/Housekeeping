package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentMetricsDTO;
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
 * 支付指标空表聚合的 MySQL 兼容 SQL 集成测试。
 */
public class PaymentMetricsMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_metrics_mapper;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMetricsMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMetricsMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMetricsMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldReturnZeroCountersWhenPaymentOrderTableIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            PaymentMetricsDTO metrics = session.getMapper(PaymentMetricsMapper.class).findSummary();

            assertEquals(0L, metrics.getTotalCount());
            assertEquals(0L, metrics.getSuccessCount());
            assertEquals(0L, metrics.getPendingCount());
            assertEquals(0L, metrics.getClosedCount());
            assertEquals("¥0.00", metrics.getSuccessAmount());
            assertEquals("0.00%", metrics.getSuccessRate());
        }
    }

    @Test
    void shouldCountOnlyVerifiedSuccessFacts() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName() + ".format\"");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, status, status_type, amount, "
                    + "channel_code, channel_transaction_no, created_at, success_at) VALUES "
                    + "('VALID', 'SUCCESS', 'success', 10.00, 'wx', 'TX-VALID', "
                    + "'2026-10-02 10:00:00.000', '2026-10-02 10:00:00.123'), "
                    + "('INVERTED', 'SUCCESS', 'success', 20.00, 'wx', 'TX-INVERTED', "
                    + "'2026-10-02 10:00:00.000', '2026-10-02 10:00:00.123'), "
                    + "('MISSING', 'SUCCESS', 'success', 30.00, 'wx', 'TX-MISSING', "
                    + "'2026-10-02 10:00:00.000', NULL), "
                    + "('PENDING', 'WAIT_CALLBACK', 'processing', 40.00, 'wx', NULL, "
                    + "'2026-10-02 10:00:00.000', NULL)");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, "
                    + "attempt_status_type, created_at, completed_at) VALUES "
                    + "('VALID', '成功', 'success', '2026-10-02 10:00:00.000', '2026-10-02 10:00:00.123'), "
                    + "('INVERTED', '成功', 'success', '2026-10-02 10:00:00.000', '2026-10-02 10:00:00.124'), "
                    + "('MISSING', '成功', 'success', '2026-10-02 10:00:00.000', '2026-10-02 10:00:00.123')");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='wx'");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='WECHAT'");
            statement.executeUpdate("UPDATE t_payment_order SET payment_method='WECHAT'");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = payment_order_id");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order");

            PaymentMetricsDTO metrics = session.getMapper(PaymentMetricsMapper.class).findSummary();

            assertEquals(4L, metrics.getTotalCount());
            assertEquals(1L, metrics.getSuccessCount());
            assertEquals(1L, metrics.getPendingCount());
            assertEquals("¥10.00", metrics.getSuccessAmount());
            assertEquals("25.00%", metrics.getSuccessRate());

            statement.executeUpdate("UPDATE t_payment_order SET status_type='SUCCESS' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status_type='SUCCESS' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_payment_order SET status_type='success ' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(0L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_payment_order SET status_type='success' "
                    + "WHERE payment_order_id='VALID'");
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status_type='success ' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(0L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status_type='success' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='virtual' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            PaymentMetricsDTO lowerCaseSource = session.getMapper(PaymentMetricsMapper.class).findSummary();
            assertEquals(0L, lowerCaseSource.getSuccessCount());
            assertEquals("¥0.00", lowerCaseSource.getSuccessAmount());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0)) "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(0L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());

            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='UNKNOWN' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            PaymentMetricsDTO unknownSource = session.getMapper(PaymentMetricsMapper.class).findSummary();
            assertEquals(0L, unknownSource.getSuccessCount());
            assertEquals("¥0.00", unknownSource.getSuccessAmount());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='STANDARD_ORDER' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(0L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("INSERT INTO t_order(order_no) VALUES ('VALID')");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='VALID'");
            session.clearCache();
            assertEquals(0L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());
            statement.executeUpdate("DELETE FROM t_order WHERE order_no='VALID'");
            session.clearCache();
            assertEquals(1L, session.getMapper(PaymentMetricsMapper.class).findSummary().getSuccessCount());

            for (String amount : new String[]{"0.00", "-5.00"}) {
                statement.executeUpdate("UPDATE t_payment_order SET amount=" + amount
                        + " WHERE payment_order_id='VALID'");
                session.clearCache();
                PaymentMetricsDTO invalidAmountMetrics = session.getMapper(PaymentMetricsMapper.class).findSummary();
                assertEquals(0L, invalidAmountMetrics.getSuccessCount());
                assertEquals("¥0.00", invalidAmountMetrics.getSuccessAmount());
            }
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "payment_order_id VARCHAR(64), order_no VARCHAR(64), "
                    + "status VARCHAR(32) NOT NULL, status_type VARCHAR(32), "
                    + "amount DECIMAL(18, 2) NOT NULL, channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), created_at TIMESTAMP, success_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR_IGNORECASE(16) DEFAULT 'VIRTUAL')");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64) NOT NULL)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(64), "
                    + "attempt_status VARCHAR(64), attempt_status_type VARCHAR(32), "
                    + "created_at TIMESTAMP, completed_at TIMESTAMP)");
            session.commit();
        }
    }
}
