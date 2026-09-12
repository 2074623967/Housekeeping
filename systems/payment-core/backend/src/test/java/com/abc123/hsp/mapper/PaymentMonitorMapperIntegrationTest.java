package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import java.io.InputStream;
import java.sql.Statement;
import java.time.LocalDate;
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
 * 支付监控聚合查询的 MySQL 兼容 SQL 集成测试。
 */
class PaymentMonitorMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_monitor;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentMonitorMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMonitorMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentMonitorMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createFixture();
    }

    @Test
    void shouldUseStableGroupKeysWhenChannelMetricsHaveEqualCounts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<PaymentChannelMetricDTO> metrics = session.getMapper(PaymentMonitorMapper.class)
                    .findChannelMetrics();

            assertEquals(3, metrics.size());
            assertEquals("channel-a", metrics.get(0).getChannelCode());
            assertEquals("method-a", metrics.get(0).getPaymentMethod());
            assertEquals("channel-a", metrics.get(1).getChannelCode());
            assertEquals("method-b", metrics.get(1).getPaymentMethod());
            assertEquals("channel-b", metrics.get(2).getChannelCode());
            assertEquals("method-a", metrics.get(2).getPaymentMethod());
            metrics.forEach(metric -> {
                assertEquals(Integer.valueOf(2), metric.getTotalCount());
                assertEquals(Integer.valueOf(1), metric.getSuccessCount());
            });
        }
    }

    @Test
    void shouldReturnMonitorAlertsInExplicitOperationsPriority() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            List<PaymentAlertItemDTO> alerts = session.getMapper(PaymentMonitorMapper.class).findAlerts();

            assertEquals(3, alerts.size());
            assertEquals("WAIT_CALLBACK_TIMEOUT", alerts.get(0).getAlertType());
            assertEquals("P1", alerts.get(0).getAlertLevel());
            assertEquals("REFUND_FAIL", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals("CHANNEL_DISABLED", alerts.get(2).getAlertType());
            assertEquals("P2", alerts.get(2).getAlertLevel());
            assertEquals("/payment-config?channelStatus=DISABLED", alerts.get(2).getActionRoute());
        }
    }

    @Test
    void shouldExposeMissingSuccessTimeAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, payment_method, status, amount) VALUES "
                    + "('PAY-SUCCESS-TIME-MISSING', 'channel-c', 'method-c', 'SUCCESS', 12.00)");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();

            assertEquals(4, alerts.size());
            assertEquals("PAYMENT_SUCCESS_TIME_MISSING", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals("/payments?status=SUCCESS_TIME_MISSING", alerts.get(1).getActionRoute());
            assertEquals(Integer.valueOf(alerts.size()), mapper.findSummary().getAlertCount());
        }
    }

    @Test
    void shouldExposeMissingRefundSuccessTimeAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status, success_at) "
                    + "VALUES ('REF-SUCCESS-TIME-MISSING', 'SUCCESS', NULL)");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, completed_at, created_at) "
                    + "VALUES ('REF-SUCCESS-TIME-MISSING', 'SUCCESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();

            assertEquals(4, alerts.size());
            assertEquals("REFUND_SUCCESS_TIME_MISSING", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals("/refunds?refundStatus=SUCCESS_TIME_MISSING", alerts.get(1).getActionRoute());
            assertEquals(Integer.valueOf(alerts.size()), mapper.findSummary().getAlertCount());
        }
    }

    @Test
    void shouldExposeRefundTerminalFactMismatchAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status, success_at) "
                    + "VALUES ('REF-TERMINAL-MISMATCH', 'SUCCESS', CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, completed_at, created_at) "
                    + "VALUES ('REF-TERMINAL-MISMATCH', 'ACCEPTED', NULL, CURRENT_TIMESTAMP)");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();

            assertEquals(4, alerts.size());
            assertEquals("REFUND_TERMINAL_FACT_MISMATCH", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals("/refunds?refundStatus=TERMINAL_FACT_MISMATCH", alerts.get(1).getActionRoute());
            assertEquals(Integer.valueOf(alerts.size()), mapper.findSummary().getAlertCount());
        }
    }

    @Test
    void shouldExposeClosedPaymentsSeparatelyInMonitorSummary() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentMonitorSummaryDTO summary = session.getMapper(PaymentMonitorMapper.class).findSummary();

            assertEquals(Integer.valueOf(6), summary.getTotalCount());
            assertEquals(Integer.valueOf(3), summary.getSuccessCount());
            assertEquals(Integer.valueOf(1), summary.getPendingPaymentCount());
            assertEquals(Integer.valueOf(1), summary.getClosedPaymentCount());
            assertEquals(Integer.valueOf(3), summary.getAlertCount());
        }
    }

    @Test
    void shouldReturnZeroValuesWhenPaymentSummaryIsEmpty() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_payment_order");

            PaymentMonitorSummaryDTO summary = session.getMapper(PaymentMonitorMapper.class).findSummary();

            assertEquals(Integer.valueOf(0), summary.getTotalCount());
            assertEquals(Integer.valueOf(0), summary.getSuccessCount());
            assertEquals(Integer.valueOf(0), summary.getPendingPaymentCount());
            assertEquals(Integer.valueOf(0), summary.getClosedPaymentCount());
            assertEquals("0.00%", summary.getSuccessRate());
            assertEquals("¥0.00", summary.getSuccessAmount());
            assertEquals(Integer.valueOf(2), summary.getAlertCount());
        }
    }

    @Test
    void shouldNotTreatUnsubmittedPrepayAsCallbackOrChannelRisk() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("DELETE FROM t_payment_order");
            statement.executeUpdate("DELETE FROM t_refund_order");
            statement.executeUpdate("DELETE FROM t_payment_channel_config");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, payment_method, status, amount) VALUES "
                    + "('PAY-PREPAY-ONLY', 'channel-a', 'method-a', 'PREPAY_CREATED', 10.00)");

            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);
            PaymentMonitorSummaryDTO summary = mapper.findSummary();

            assertEquals(Integer.valueOf(1), summary.getPendingPaymentCount());
            assertEquals(Integer.valueOf(0), summary.getAlertCount());
            assertEquals(List.of(), mapper.findAlerts());
            assertEquals(List.of(), mapper.findChannelMetrics());
        }
    }

    @Test
    void shouldAssignRecentTrendFactsToCreationAndSuccessDates() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, payment_method, status, amount, success_at, created_at) VALUES "
                    + "('PAY-CROSS-WINDOW', 'channel-a', 'method-a', 'SUCCESS', 20.00, "
                    + "CURRENT_TIMESTAMP, DATEADD('DAY', -7, CURRENT_TIMESTAMP)), "
                    + "('PAY-CROSS-DAY', 'channel-a', 'method-a', 'SUCCESS', 30.00, "
                    + "DATEADD('DAY', -5, CURRENT_TIMESTAMP), DATEADD('DAY', -6, CURRENT_TIMESTAMP)), "
                    + "('PAY-FUTURE', 'channel-a', 'method-a', 'SUCCESS', 40.00, "
                    + "DATEADD('DAY', 1, CURRENT_TIMESTAMP), DATEADD('DAY', 1, CURRENT_TIMESTAMP))");

            List<PaymentTrendPointDTO> trends = session.getMapper(PaymentMonitorMapper.class)
                    .findRecentTrends();

            assertEquals(3, trends.size());
            assertEquals(LocalDate.now().toString(), trends.get(0).getStatDate());
            assertEquals(Integer.valueOf(6), trends.get(0).getTotalCount());
            assertEquals(Integer.valueOf(4), trends.get(0).getSuccessCount());
            assertEquals("¥50.00", trends.get(0).getSuccessAmount());
            assertEquals(LocalDate.now().minusDays(5).toString(), trends.get(1).getStatDate());
            assertEquals(Integer.valueOf(0), trends.get(1).getTotalCount());
            assertEquals(Integer.valueOf(1), trends.get(1).getSuccessCount());
            assertEquals("¥30.00", trends.get(1).getSuccessAmount());
            assertEquals(LocalDate.now().minusDays(6).toString(), trends.get(2).getStatDate());
            assertEquals(Integer.valueOf(1), trends.get(2).getTotalCount());
            assertEquals(Integer.valueOf(0), trends.get(2).getSuccessCount());
            assertEquals("¥0.00", trends.get(2).getSuccessAmount());
        }
    }

    private void createFixture() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR \""
                    + PaymentTaskCenterMapperIntegrationTest.class.getName()
                    + ".dateFormat\"");
            statement.execute("CREATE ALIAS IF NOT EXISTS FORMAT FOR \""
                    + PaymentReturnCodeSnapshotMapperIntegrationTest.class.getName()
                    + ".format\"");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), channel_code VARCHAR(64), "
                    + "payment_method VARCHAR(32), status VARCHAR(32), amount DECIMAL(18, 2), success_at TIMESTAMP, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "status VARCHAR(32), success_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "attempt_status VARCHAR(32), completed_at TIMESTAMP, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(32))");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64))");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, payment_method, status, amount) VALUES "
                    + "('PAY-001', 'channel-b', 'method-a', 'SUCCESS', 10.00), "
                    + "('PAY-002', 'channel-b', 'method-a', 'WAIT_CALLBACK', 10.00), "
                    + "('PAY-003', 'channel-a', 'method-b', 'SUCCESS', 10.00), "
                    + "('PAY-004', 'channel-a', 'method-b', 'FAIL', 10.00), "
                    + "('PAY-005', 'channel-a', 'method-a', 'SUCCESS', 10.00), "
                    + "('PAY-006', 'channel-a', 'method-a', 'CLOSED', 10.00)");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = created_at WHERE status = 'SUCCESS'");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id) VALUES "
                    + "('PAY-001'), ('PAY-001'), ('PAY-002'), ('PAY-003'), ('PAY-004'), ('PAY-005'), ('PAY-006')");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status) VALUES ('REF-FAIL', 'FAIL')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, attempt_status, completed_at, created_at) "
                    + "VALUES ('REF-FAIL', 'FAIL', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_payment_channel_config(status) VALUES ('DISABLED')");
            session.commit();
        }
    }
}
