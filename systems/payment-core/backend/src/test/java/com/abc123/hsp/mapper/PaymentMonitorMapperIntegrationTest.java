package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentAlertItemDTO;
import com.abc123.hsp.dto.PaymentChannelMetricDTO;
import com.abc123.hsp.dto.PaymentMonitorSummaryDTO;
import com.abc123.hsp.dto.PaymentTrendPointDTO;
import java.io.InputStream;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.Collections;
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
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/PaymentMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/RefundMapper.xml")) {
            new XMLMapperBuilder(mapperXml, configuration, "mapper/RefundMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
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
    void shouldCapExportChannelMetricsWithoutTruncatingOverview() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement orders = session.getConnection().prepareStatement(
                     "INSERT INTO t_payment_order(payment_order_id, channel_code, payment_method, status, amount) "
                             + "VALUES (?, ?, 'bulk', 'CLOSED', 1.00)");
             PreparedStatement attempts = session.getConnection().prepareStatement(
                     "INSERT INTO t_payment_attempt(payment_order_id, attempt_status) VALUES (?, '处理中')")) {
            for (int groupIndex = 0; groupIndex < 1002; groupIndex++) {
                String orderId = "PAY-BULK-" + groupIndex;
                orders.setString(1, orderId);
                orders.setString(2, "bulk-" + groupIndex);
                orders.addBatch();
                attempts.setString(1, orderId);
                attempts.addBatch();
            }
            orders.executeBatch();
            attempts.executeBatch();
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            assertEquals(1005, mapper.findChannelMetrics().size());
            List<PaymentChannelMetricDTO> exportMetrics = mapper.findChannelMetricsForExport();
            assertEquals(1001, exportMetrics.size());
            assertEquals("channel-a", exportMetrics.get(0).getChannelCode());
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
    void shouldDistinguishUnacceptedChannelSubmissionFromAcceptedCallbackWithoutHidingIt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);
            statement.executeUpdate("UPDATE t_payment_attempt SET attempt_status = '渠道提交中' "
                    + "WHERE payment_order_id = 'PAY-002'");
            session.clearCache();

            List<PaymentAlertItemDTO> beforeAcceptance = mapper.findAlerts();
            org.junit.jupiter.api.Assertions.assertFalse(beforeAcceptance.stream()
                    .anyMatch(alert -> "WAIT_CALLBACK_TIMEOUT".equals(alert.getAlertType())));
            PaymentAlertItemDTO submission = beforeAcceptance.stream()
                    .filter(alert -> "CHANNEL_SUBMIT_UNCONFIRMED".equals(alert.getAlertType()))
                    .findFirst().get();
            assertEquals(Integer.valueOf(1), submission.getAffectedCount());
            org.junit.jupiter.api.Assertions.assertTrue(submission.getSuggestedAction().contains("勿主动查单"));
            assertEquals(Integer.valueOf(3), mapper.findSummary().getAlertCount());
            PaymentChannelMetricDTO pendingChannel = mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-b".equals(metric.getChannelCode())).findFirst().get();
            assertEquals(Integer.valueOf(0), pendingChannel.getPendingCount());
            assertEquals(Integer.valueOf(1), pendingChannel.getUnacceptedCount());
            org.junit.jupiter.api.Assertions.assertTrue(pendingChannel.getRiskHint().contains("核对渠道受理"));

            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no = 'TX-ACCEPTED' "
                    + "WHERE payment_order_id = 'PAY-002'");
            session.clearCache();

            List<PaymentAlertItemDTO> afterAcceptance = mapper.findAlerts();
            org.junit.jupiter.api.Assertions.assertFalse(afterAcceptance.stream()
                    .anyMatch(alert -> "CHANNEL_SUBMIT_UNCONFIRMED".equals(alert.getAlertType())));
            assertEquals(Integer.valueOf(1), afterAcceptance.stream()
                    .filter(alert -> "WAIT_CALLBACK_TIMEOUT".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
            assertEquals(Integer.valueOf(1), mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-b".equals(metric.getChannelCode()))
                    .findFirst().get().getPendingCount());
            assertEquals(Integer.valueOf(0), mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-b".equals(metric.getChannelCode()))
                    .findFirst().get().getUnacceptedCount());

            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no = NULL "
                    + "WHERE payment_order_id = 'PAY-002'");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, channel_code, "
                    + "channel_transaction_no, payment_method, status, amount) VALUES "
                    + "('PAY-ACCEPTED-CALLBACK', 'channel-b', 'TX-OTHER', 'method-a', 'WAIT_CALLBACK', 10.00)");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) "
                    + "VALUES ('PAY-ACCEPTED-CALLBACK', '处理中', CURRENT_TIMESTAMP)");
            session.clearCache();

            List<PaymentAlertItemDTO> mixedAlerts = mapper.findAlerts();
            assertEquals(Integer.valueOf(1), mixedAlerts.stream()
                    .filter(alert -> "WAIT_CALLBACK_TIMEOUT".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
            assertEquals(Integer.valueOf(1), mixedAlerts.stream()
                    .filter(alert -> "CHANNEL_SUBMIT_UNCONFIRMED".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
            assertEquals(Integer.valueOf(4), mapper.findSummary().getAlertCount());
            assertEquals(Integer.valueOf(1), mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-b".equals(metric.getChannelCode()))
                    .findFirst().get().getPendingCount());
            assertEquals(Integer.valueOf(1), mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-b".equals(metric.getChannelCode()))
                    .findFirst().get().getUnacceptedCount());
        }
    }

    @Test
    void shouldExposeMissingSuccessTimeAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, payment_method, status, amount) VALUES "
                    + "('PAY-SUCCESS-TIME-MISSING', 'channel-c', 'TX-TIME-MISSING', 'method-c', 'SUCCESS', 12.00)");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-SUCCESS-TIME-MISSING', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
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
    void shouldExposePaymentSuccessFactMismatchAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, payment_method, status, amount, success_at) VALUES "
                    + "('PAY-SUCCESS-FACT-MISMATCH', 'channel-c', 'TX-FACT-MISMATCH', 'method-c', 'SUCCESS', 12.00, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-SUCCESS-FACT-MISMATCH', '处理中', 'processing', NULL, CURRENT_TIMESTAMP)");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();

            assertEquals(4, alerts.size());
            assertEquals("PAYMENT_SUCCESS_FACT_MISMATCH", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals("/payments?status=SUCCESS_FACT_MISMATCH", alerts.get(1).getActionRoute());
            assertEquals("支付终态与渠道尝试、预付绑定或订单来源不一致，需核查原始事实。",
                    alerts.get(1).getAlertMessage());
            assertEquals("按支付单号核对预付单、订单来源、最新尝试、渠道流水和操作日志，不得自动补标。",
                    alerts.get(1).getSuggestedAction());
            assertEquals(Integer.valueOf(alerts.size()), mapper.findSummary().getAlertCount());
        }
    }

    @Test
    void shouldAlertWhenPaymentAttemptCompletesAfterSuccessWithinSameSecond() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, payment_method, status, "
                    + "amount, success_at, created_at) VALUES ("
                    + "'PAY-FRACTIONAL-TIMELINE', 'channel-c', 'TX-FRACTIONAL', 'method-c', 'SUCCESS', "
                    + "12.00, '2026-10-02 10:00:00.123', '2026-10-02 09:59:59.999')");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, channel_code, payment_method, attempt_status, attempt_status_type, completed_at, created_at) VALUES ("
                    + "'PAY-FRACTIONAL-TIMELINE', 'channel-c', 'method-c', '成功', 'success', "
                    + "'2026-10-02 10:00:00.124', '2026-10-02 09:59:59.999')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = payment_order_id "
                    + "WHERE payment_order_id = 'PAY-FRACTIONAL-TIMELINE'");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order "
                    + "WHERE payment_order_id = 'PAY-FRACTIONAL-TIMELINE'");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();
            assertEquals(4, alerts.size());
            assertEquals("PAYMENT_SUCCESS_FACT_MISMATCH", alerts.get(1).getAlertType());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals(Integer.valueOf(4), mapper.findSummary().getAlertCount());

            statement.executeUpdate("UPDATE t_payment_attempt SET completed_at='2026-10-02 10:00:00.122' "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            session.clearCache();
            assertEquals(3, mapper.findAlerts().size());
            assertEquals(Integer.valueOf(3), mapper.findSummary().getAlertCount());

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='other_channel' "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            session.clearCache();
            assertEquals(4, mapper.findAlerts().size());
            assertEquals(Integer.valueOf(4), mapper.findSummary().getAlertCount());

            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-c' "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='virtual' "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            session.clearCache();
            assertEquals(4, mapper.findAlerts().size());
            assertEquals(Integer.valueOf(1), mapper.findAlerts().get(1).getAffectedCount());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind=CONCAT('VIRTUAL', CHAR(0)) "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            session.clearCache();
            assertEquals(4, mapper.findAlerts().size());
            statement.executeUpdate("UPDATE t_prepay_order SET source_kind='VIRTUAL' "
                    + "WHERE payment_order_id='PAY-FRACTIONAL-TIMELINE'");
            session.clearCache();
            assertEquals(3, mapper.findAlerts().size());
            assertEquals(Integer.valueOf(3), mapper.findSummary().getAlertCount());
        }
    }

    @Test
    void shouldExposeMissingRefundSuccessTimeAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, status, status_type, applied_at, channel_code, channel_refund_no, "
                    + "channel_submitted_at, success_at, terminal_at) "
                    + "VALUES ('REF-SUCCESS-TIME-MISSING', 'SUCCESS', 'success', '2026-09-11 08:00:00', "
                    + "'wx_h5', 'CH-TIME-MISSING', '2026-09-11 08:01:00', NULL, '2026-09-11 09:00:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-SUCCESS-TIME-MISSING', 'wx_h5', 'CH-TIME-MISSING', 'SUCCESS', 'success', "
                    + "'2026-09-11 09:00:00', '2026-09-11 08:01:00')");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();

            assertEquals(4, alerts.size());
            assertEquals("REFUND_SUCCESS_TIME_MISSING", alerts.get(1).getAlertType());
            assertEquals("P1", alerts.get(1).getAlertLevel());
            assertEquals(Integer.valueOf(1), alerts.get(1).getAffectedCount());
            assertEquals("/refunds?refundStatus=SUCCESS_TIME_MISSING", alerts.get(1).getActionRoute());
            assertEquals(0, alerts.stream().filter(alert -> "REFUND_TERMINAL_FACT_MISMATCH".equals(alert.getAlertType()))
                    .count());
            assertEquals(Integer.valueOf(alerts.size()), mapper.findSummary().getAlertCount());

            statement.executeUpdate("UPDATE t_refund_attempt SET attempt_status_type = 'warn' "
                    + "WHERE refund_order_id = 'REF-SUCCESS-TIME-MISSING'");
            session.clearCache();
            assertEquals(Integer.valueOf(1), mapper.findAlerts().stream()
                    .filter(alert -> "REFUND_TERMINAL_FACT_MISMATCH".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
        }
    }

    @Test
    void shouldExposeRefundTerminalFactMismatchAsGlobalP1Alert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order("
                    + "refund_order_id, status, success_at, terminal_at) "
                    + "VALUES ('REF-TERMINAL-MISMATCH', 'SUCCESS', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
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
    void shouldAlertWhenRefundAttemptCompletesAfterSuccessTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status, status_type, applied_at, "
                    + "channel_code, channel_refund_no, channel_submitted_at, success_at, terminal_at) VALUES "
                    + "('REF-LATE', 'SUCCESS', 'success', '2026-09-12 08:00:00', 'wx_h5', 'CH-LATE', "
                    + "'2026-09-12 08:01:00', '2026-09-12 09:00:00', '2026-09-12 09:01:00')");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('REF-LATE', 'wx_h5', 'CH-LATE', 'SUCCESS', 'success', "
                    + "'2026-09-12 09:00:00.001', '2026-09-12 08:01:00')");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);
            List<PaymentAlertItemDTO> alerts = mapper.findAlerts();
            assertEquals(Integer.valueOf(1), alerts.stream()
                    .filter(alert -> "REFUND_TERMINAL_FACT_MISMATCH".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
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
    void shouldExcludeUnverifiedSuccessFromSummaryTrendAndChannelExport() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, channel_code, "
                    + "channel_transaction_no, payment_method, status, amount, success_at, created_at) VALUES "
                    + "('PAY-VERIFIED', 'channel-c', 'TX-VERIFIED', 'method-c', 'SUCCESS', 50.00, "
                    + "DATEADD('SECOND', 1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP), "
                    + "('PAY-INVERTED', 'channel-c', 'TX-INVERTED', 'method-c', 'SUCCESS', 20.00, "
                    + "DATEADD('MILLISECOND', 1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP), "
                    + "('PAY-MISSING', 'channel-c', 'TX-MISSING', 'method-c', 'SUCCESS', 30.00, "
                    + "NULL, CURRENT_TIMESTAMP), "
                    + "('PAY-NO-ATTEMPT', 'channel-c', 'TX-NO-ATTEMPT', 'method-c', 'SUCCESS', 40.00, "
                    + "CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, "
                    + "attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-VERIFIED', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('PAY-INVERTED', '成功', 'success', "
                    + "DATEADD('MILLISECOND', 2, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP), "
                    + "('PAY-MISSING', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-c' "
                    + "WHERE payment_order_id IN ('PAY-VERIFIED', 'PAY-INVERTED', 'PAY-MISSING')");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='method-c' "
                    + "WHERE payment_order_id IN ('PAY-VERIFIED', 'PAY-INVERTED', 'PAY-MISSING')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = payment_order_id "
                    + "WHERE payment_order_id IN ('PAY-VERIFIED', 'PAY-INVERTED', 'PAY-MISSING', 'PAY-NO-ATTEMPT')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order "
                    + "WHERE payment_order_id IN ('PAY-VERIFIED', 'PAY-INVERTED', 'PAY-MISSING', 'PAY-NO-ATTEMPT')");
            PaymentMonitorMapper mapper = session.getMapper(PaymentMonitorMapper.class);

            PaymentMonitorSummaryDTO summary = mapper.findSummary();
            assertEquals(Integer.valueOf(10), summary.getTotalCount());
            assertEquals(Integer.valueOf(4), summary.getSuccessCount());
            assertEquals("40.00%", summary.getSuccessRate());
            assertEquals("¥80.00", summary.getSuccessAmount());
            assertEquals(Integer.valueOf(5), summary.getAlertCount());

            PaymentTrendPointDTO today = mapper.findRecentTrends().get(0);
            assertEquals(LocalDate.now().toString(), today.getStatDate());
            assertEquals(Integer.valueOf(10), today.getTotalCount());
            assertEquals(Integer.valueOf(4), today.getSuccessCount());
            assertEquals("¥80.00", today.getSuccessAmount());

            PaymentChannelMetricDTO channel = mapper.findChannelMetrics().stream()
                    .filter(metric -> "channel-c".equals(metric.getChannelCode()))
                    .findFirst().get();
            PaymentChannelMetricDTO exported = mapper.findChannelMetricsForExport().stream()
                    .filter(metric -> "channel-c".equals(metric.getChannelCode()))
                    .findFirst().get();
            assertEquals(Integer.valueOf(3), channel.getTotalCount());
            assertEquals(Integer.valueOf(1), channel.getSuccessCount());
            assertEquals("33.33%", channel.getSuccessRate());
            assertEquals("¥50.00", channel.getSuccessAmount());
            assertEquals(channel.getSuccessCount(), exported.getSuccessCount());
            assertEquals(channel.getSuccessAmount(), exported.getSuccessAmount());
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
            assertEquals(Integer.valueOf(3), summary.getAlertCount());
            assertEquals(Integer.valueOf(1), session.getMapper(PaymentMonitorMapper.class).findAlerts().stream()
                    .filter(alert -> "REFUND_TERMINAL_FACT_MISMATCH".equals(alert.getAlertType()))
                    .findFirst().get().getAffectedCount());
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
            assertEquals(Collections.emptyList(), mapper.findAlerts());
            assertEquals(Collections.emptyList(), mapper.findChannelMetrics());
        }
    }

    @Test
    void shouldAssignRecentTrendFactsToCreationAndSuccessDates() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, payment_method, status, amount, success_at, created_at) VALUES "
                    + "('PAY-CROSS-WINDOW', 'channel-a', 'TX-CROSS-WINDOW', 'method-a', 'SUCCESS', 20.00, "
                    + "CURRENT_TIMESTAMP, DATEADD('DAY', -7, CURRENT_TIMESTAMP)), "
                    + "('PAY-CROSS-DAY', 'channel-a', 'TX-CROSS-DAY', 'method-a', 'SUCCESS', 30.00, "
                    + "DATEADD('DAY', -5, CURRENT_TIMESTAMP), DATEADD('DAY', -6, CURRENT_TIMESTAMP)), "
                    + "('PAY-FUTURE', 'channel-a', 'TX-FUTURE', 'method-a', 'SUCCESS', 40.00, "
                    + "DATEADD('DAY', 1, CURRENT_TIMESTAMP), DATEADD('DAY', 1, CURRENT_TIMESTAMP))");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, "
                    + "attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-CROSS-WINDOW', '成功', 'success', CURRENT_TIMESTAMP, "
                    + "DATEADD('DAY', -7, CURRENT_TIMESTAMP)), "
                    + "('PAY-CROSS-DAY', '成功', 'success', DATEADD('DAY', -5, CURRENT_TIMESTAMP), "
                    + "DATEADD('DAY', -6, CURRENT_TIMESTAMP)), "
                    + "('PAY-FUTURE', '成功', 'success', DATEADD('DAY', 1, CURRENT_TIMESTAMP), "
                    + "DATEADD('DAY', 1, CURRENT_TIMESTAMP))");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code='channel-a' "
                    + "WHERE payment_order_id IN ('PAY-CROSS-WINDOW', 'PAY-CROSS-DAY', 'PAY-FUTURE')");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method='method-a' "
                    + "WHERE payment_order_id IN ('PAY-CROSS-WINDOW', 'PAY-CROSS-DAY', 'PAY-FUTURE')");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = DATEADD('SECOND', 1, success_at) "
                    + "WHERE payment_order_id IN ('PAY-CROSS-WINDOW', 'PAY-CROSS-DAY', 'PAY-FUTURE')");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = payment_order_id "
                    + "WHERE payment_order_id IN ('PAY-CROSS-WINDOW', 'PAY-CROSS-DAY', 'PAY-FUTURE')");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order "
                    + "WHERE payment_order_id IN ('PAY-CROSS-WINDOW', 'PAY-CROSS-DAY', 'PAY-FUTURE')");

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
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), "
                    + "payment_method VARCHAR(32), status VARCHAR(32), status_type VARCHAR(32) DEFAULT 'success', amount DECIMAL(18, 2), success_at TIMESTAMP, "
                    + "created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)");
            statement.execute("CREATE TABLE t_prepay_order (payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR(64), amount DECIMAL(18,2), "
                    + "source_kind VARCHAR_IGNORECASE(16) DEFAULT 'VIRTUAL')");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order (order_no VARCHAR(64))");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "payment_order_id VARCHAR(64) NOT NULL DEFAULT 'PAY-001', "
                    + "refund_amount DECIMAL(18, 2) NOT NULL DEFAULT 1.00, "
                    + "status VARCHAR(32), status_type VARCHAR(32), applied_at TIMESTAMP, "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), channel_submitted_at TIMESTAMP, "
                    + "success_at TIMESTAMP, terminal_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), "
                    + "attempt_status VARCHAR(32), attempt_status_type VARCHAR(32), "
                    + "completed_at TIMESTAMP, created_at TIMESTAMP)");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, status VARCHAR(32))");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), payment_method VARCHAR(32), "
                    + "attempt_status VARCHAR(64), attempt_status_type VARCHAR(32), completed_at TIMESTAMP, created_at TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, payment_method, status, amount) VALUES "
                    + "('PAY-001', 'channel-b', 'TX-001', 'method-a', 'SUCCESS', 10.00), "
                    + "('PAY-002', 'channel-b', NULL, 'method-a', 'WAIT_CALLBACK', 10.00), "
                    + "('PAY-003', 'channel-a', 'TX-003', 'method-b', 'SUCCESS', 10.00), "
                    + "('PAY-004', 'channel-a', NULL, 'method-b', 'FAIL', 10.00), "
                    + "('PAY-005', 'channel-a', 'TX-005', 'method-a', 'SUCCESS', 10.00), "
                    + "('PAY-006', 'channel-a', NULL, 'method-a', 'CLOSED', 10.00)");
            statement.executeUpdate("INSERT INTO t_payment_attempt("
                    + "payment_order_id, attempt_status, attempt_status_type, completed_at, created_at) VALUES "
                    + "('PAY-001', '成功', 'success', CURRENT_TIMESTAMP, DATEADD('SECOND', -1, CURRENT_TIMESTAMP)), "
                    + "('PAY-001', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('PAY-002', '处理中', 'processing', NULL, CURRENT_TIMESTAMP), "
                    + "('PAY-003', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('PAY-004', '失败', 'failed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('PAY-005', '成功', 'success', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP), "
                    + "('PAY-006', '处理中', 'processing', NULL, CURRENT_TIMESTAMP)");
            statement.executeUpdate("UPDATE t_payment_attempt SET channel_code = "
                    + "(SELECT p.channel_code FROM t_payment_order p "
                    + "WHERE p.payment_order_id = t_payment_attempt.payment_order_id)");
            statement.executeUpdate("UPDATE t_payment_attempt SET payment_method = "
                    + "(SELECT p.payment_method FROM t_payment_order p "
                    + "WHERE p.payment_order_id = t_payment_attempt.payment_order_id)");
            statement.executeUpdate("UPDATE t_payment_order "
                    + "SET success_at = DATEADD('SECOND', 1, CURRENT_TIMESTAMP) WHERE status = 'SUCCESS'");
            statement.executeUpdate("UPDATE t_payment_order SET order_no = payment_order_id");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, order_no, amount) "
                    + "SELECT payment_order_id, order_no, amount FROM t_payment_order");
            statement.executeUpdate("INSERT INTO t_refund_order(refund_order_id, status, status_type, applied_at, "
                    + "channel_code, channel_refund_no, channel_submitted_at, terminal_at) VALUES "
                    + "('REF-FAIL', 'FAIL', 'danger', DATEADD('SECOND', -2, CURRENT_TIMESTAMP), 'wx_h5', 'CH-FAIL', "
                    + "DATEADD('SECOND', -1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)");
            statement.executeUpdate("INSERT INTO t_refund_attempt(refund_order_id, channel_code, channel_refund_no, "
                    + "attempt_status, attempt_status_type, completed_at, created_at) "
                    + "VALUES ('REF-FAIL', 'wx_h5', 'CH-FAIL', 'FAIL', 'danger', "
                    + "CURRENT_TIMESTAMP, DATEADD('SECOND', -1, CURRENT_TIMESTAMP))");
            statement.executeUpdate("UPDATE t_refund_order "
                    + "SET terminal_at = DATEADD('SECOND', 1, CURRENT_TIMESTAMP) WHERE refund_order_id = 'REF-FAIL'");
            statement.executeUpdate("INSERT INTO t_payment_channel_config(status) VALUES ('DISABLED')");
            session.commit();
        }
    }
}
