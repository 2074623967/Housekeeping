package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentQueryTrendPointDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.exceptions.PersistenceException;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * 支付任务中心 Mapper 的 MySQL 兼容 SQL 集成测试。
 */
class PaymentTaskCenterMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_task_center;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment(
                "test",
                new JdbcTransactionFactory(),
                dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentTaskCenterMapper.class);
        configuration.addMapper(PaymentIssueMapper.class);
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentTaskCenterMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentTaskCenterMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        try (InputStream mapperXml = Resources.getResourceAsStream("mapper/PaymentIssueMapper.xml")) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    "mapper/PaymentIssueMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldQuerySubmittedPaymentsAfterPrepayExpiryAndPreserveBackoffRules() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            List<String> automaticCandidates = mapper.findDuePendingPaymentOrderIdsForQuery(
                    30, 60, 2, 900, 20, 100);
            List<String> channelLimitedAutomaticCandidates = mapper.findDuePendingPaymentOrderIdsForQuery(
                    30, 60, 2, 900, 1, 100);
            List<String> manualCandidates = mapper.findPendingPaymentOrderIdsForManualQuery(30, 20, 100);
            List<String> channelLimitedManualCandidates = mapper.findPendingPaymentOrderIdsForManualQuery(
                    30, 2, 100);

            assertEquals(Arrays.asList("PAY-NO-HISTORY", "PAY-BACKOFF-DUE", "PAY-MAX-DUE"),
                    automaticCandidates);
            assertEquals(Arrays.asList("PAY-NO-HISTORY", "PAY-MAX-DUE"),
                    channelLimitedAutomaticCandidates);
            assertEquals(Arrays.asList(
                    "PAY-NO-HISTORY",
                    "PAY-BASE-WAIT",
                    "PAY-BACKOFF-WAIT",
                    "PAY-BACKOFF-DUE",
                    "PAY-MAX-WAIT",
                    "PAY-MAX-DUE"), manualCandidates);
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("PAY-FRESH-ATTEMPT"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("PAY-NO-ATTEMPT"));
            org.junit.jupiter.api.Assertions.assertTrue(manualCandidates.contains("PAY-NO-HISTORY"));
            org.junit.jupiter.api.Assertions.assertTrue(automaticCandidates.contains("PAY-NO-HISTORY"));
            assertEquals(Arrays.asList(
                    "PAY-NO-HISTORY",
                    "PAY-BASE-WAIT",
                    "PAY-MAX-WAIT",
                    "PAY-MAX-DUE"), channelLimitedManualCandidates);
            assertEquals(6, mapper.countPendingPaymentQueryBacklog(30));
            assertEquals(3, mapper.countDuePendingPaymentQueries(30, 60, 2, 900));
        }
    }

    @Test
    void shouldQueryProcessingRefundsWithBackoffAndChannelQuotas() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            List<String> automaticCandidates = mapper.findDuePendingRefundOrderIdsForQuery(
                    30, 60, 2, 900, 20, 100);
            List<String> channelLimitedAutomaticCandidates = mapper.findDuePendingRefundOrderIdsForQuery(
                    30, 60, 2, 900, 1, 100);
            List<String> manualCandidates = mapper.findPendingRefundOrderIdsForManualQuery(30, 20, 100);

            assertEquals(Arrays.asList("REF-NO-HISTORY", "REF-BACKOFF-DUE", "REF-MAX-DUE"),
                    automaticCandidates);
            assertEquals(Arrays.asList("REF-NO-HISTORY", "REF-MAX-DUE"),
                    channelLimitedAutomaticCandidates);
            assertEquals(Arrays.asList(
                    "REF-NO-HISTORY",
                    "REF-BASE-WAIT",
                    "REF-BACKOFF-WAIT",
                    "REF-BACKOFF-DUE",
                    "REF-MAX-WAIT",
                    "REF-MAX-DUE"), manualCandidates);
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-FRESH"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-NO-TRACE"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-SUCCESS"));
            assertEquals(6, mapper.countPendingRefundQueryBacklog(30));
            assertEquals(3, mapper.countDuePendingRefundQueries(30, 60, 2, 900));
        }
    }

    @Test
    void shouldSummarizeRecentPaymentQueryTrendWithinLookbackWindow() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(15, mapper.countRecentPaymentQueries(24));
            assertEquals(1, mapper.countRecentPaymentQueryFailures(24));
            assertEquals(5, mapper.countRecentChannelQueryRateLimitRejections(24));
            List<PaymentQueryTrendPointDTO> trends = mapper.findRecentPaymentQueryTrends(24);

            assertEquals(15, trends.stream().mapToInt(PaymentQueryTrendPointDTO::getQueryCount).sum());
            assertEquals(1, trends.stream().mapToInt(PaymentQueryTrendPointDTO::getFailureCount).sum());
        }
    }

    @Test
    void shouldOnlyCountRecentUnacknowledgedQueryMonitorOutboxAlerts() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(1, mapper.countRecentUnacknowledgedQueryMonitorAlerts(
                    "主动查单失败趋势", 60));
            assertEquals(0, mapper.countRecentUnacknowledgedQueryMonitorAlerts(
                    "主动查单限流拒绝", 60));
        }
    }

    @Test
    void shouldCountOnlyActionableIssueAlertDeliveryBacklogs() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(2, mapper.countPendingIssueAlertDispatches());
            assertEquals(1, mapper.countFailedIssueAlertDispatches());
            assertEquals(45, mapper.findOldestPendingIssueAlertDispatchMinutes());
            assertEquals(1, mapper.countPendingIssueAlertReceipts());
            assertEquals(65, mapper.findOldestPendingIssueAlertReceiptMinutes());
        }
    }

    @Test
    void shouldRejectDuplicateIssueAlertGenerationKeyWithoutOverwritingOriginalAlert() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            PaymentIssueAlertLogEntity original = buildIssueAlertLog("PIA-IDEMPOTENT-1");
            PaymentIssueAlertLogEntity duplicate = buildIssueAlertLog("PIA-IDEMPOTENT-2");

            assertEquals(1, mapper.insertIssueAlertLog(original));
            org.junit.jupiter.api.Assertions.assertThrows(
                    PersistenceException.class,
                    () -> mapper.insertIssueAlertLog(duplicate));
            assertEquals(1, countAlertsByGenerationKey(
                    session.getConnection(), "ISSUE_ACTIVE:ISSUE-IDEMPOTENT"));
            assertEquals("PIA-IDEMPOTENT-1", findAlertNoByGenerationKey(
                    session.getConnection(), "ISSUE_ACTIVE:ISSUE-IDEMPOTENT"));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    @Test
    void shouldReleaseActiveIssueGenerationKeyAfterAcknowledgement() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper taskCenterMapper = session.getMapper(PaymentTaskCenterMapper.class);
            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueAlertLogEntity original = buildIssueAlertLog("PIA-ACK-1");
            PaymentIssueAlertLogEntity replacement = buildIssueAlertLog("PIA-ACK-2");

            assertEquals(1, taskCenterMapper.insertIssueAlertLog(original));
            assertEquals(1, issueMapper.acknowledgeAlertByAlertNo("PIA-ACK-1", "支付运营"));
            assertEquals(1, taskCenterMapper.insertIssueAlertLog(replacement));
            assertEquals(1, countAlertsByGenerationKey(
                    session.getConnection(), "ISSUE_ACTIVE:ISSUE-IDEMPOTENT"));
        }
    }

    @Test
    void shouldFenceTaskLeaseOwnersAcrossExpiry() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(1, mapper.initTaskLease("PAYMENT_LEASE_TEST"));
            assertEquals(1, mapper.acquireTaskLease("PAYMENT_LEASE_TEST", "owner-a", 120));
            assertEquals(0, mapper.acquireTaskLease("PAYMENT_LEASE_TEST", "owner-a", 120));
            assertEquals(0, mapper.acquireTaskLease("PAYMENT_LEASE_TEST", "owner-b", 120));
            assertEquals(0, mapper.renewTaskLease("PAYMENT_LEASE_TEST", "owner-b", 120));
            assertEquals(1, mapper.renewTaskLease("PAYMENT_LEASE_TEST", "owner-a", 120));
            assertEquals(0, mapper.releaseTaskLease("PAYMENT_LEASE_TEST", "owner-b"));
            expireTaskLease(session.getConnection(), "PAYMENT_LEASE_TEST");
            assertEquals(0, mapper.renewTaskLease("PAYMENT_LEASE_TEST", "owner-a", 120));
            assertEquals(1, mapper.acquireTaskLease("PAYMENT_LEASE_TEST", "owner-b", 120));
            assertEquals(0, mapper.releaseTaskLease("PAYMENT_LEASE_TEST", "owner-a"));
            assertEquals(1, mapper.releaseTaskLease("PAYMENT_LEASE_TEST", "owner-b"));
            session.commit();
        }
    }

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            Connection connection = session.getConnection();
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_query_rate_limit_log");
            statement.execute("DROP TABLE IF EXISTS t_refund_channel_query_rate_limit_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_task_lease");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("DROP TABLE IF EXISTS t_refund_operation_log");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "payment_order_id VARCHAR(64), expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "attempt_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), "
                    + "channel_submitted_at DATETIME, status VARCHAR(32), applied_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_operation_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "action_code VARCHAR(64), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_channel_query_rate_limit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rejection_no VARCHAR(64), "
                    + "channel_code VARCHAR(64), payment_order_id VARCHAR(64), "
                    + "window_seconds INT, max_queries INT, rejected_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_channel_query_rate_limit_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, rejection_no VARCHAR(64), "
                    + "channel_code VARCHAR(64), refund_order_id VARCHAR(64), "
                    + "window_seconds INT, max_queries INT, rejected_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_task_lease ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, task_code VARCHAR(64) NOT NULL UNIQUE, "
                    + "lock_owner VARCHAR(128), lock_expires_at DATETIME, updated_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_issue_alert_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, alert_no VARCHAR(64), source_alert_no VARCHAR(64), "
                    + "generation_key VARCHAR(191), issue_no VARCHAR(128), payment_order_id VARCHAR(64), "
                    + "issue_type VARCHAR(64), severity VARCHAR(32), responsibility_group VARCHAR(64), "
                    + "alert_channel VARCHAR(32), receiver VARCHAR(64), alert_status VARCHAR(32), "
                    + "alert_status_type VARCHAR(32), ack_status VARCHAR(32), ack_status_type VARCHAR(32), "
                    + "alert_content VARCHAR(512), provider_code VARCHAR(64), provider_name VARCHAR(128), "
                    + "endpoint_alias VARCHAR(128), template_code VARCHAR(128), "
                    + "provider_receipt_snapshot VARCHAR(2048), provider_receipt_no VARCHAR(128), "
                    + "provider_delivery_status VARCHAR(64), provider_delivery_message VARCHAR(255), "
                    + "rendered_content_snapshot VARCHAR(1024), triggered_by VARCHAR(64), "
                    + "ack_operator VARCHAR(64), ack_at DATETIME, created_at DATETIME, "
                    + "UNIQUE (alert_no), UNIQUE (generation_key))");

            LocalDateTime now = LocalDateTime.now();
            insertPayment(connection, "PAY-NO-HISTORY", "wx_h5", now.minusMinutes(10));
            statement.executeUpdate("UPDATE t_prepay_order SET expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE payment_order_id = 'PAY-NO-HISTORY'");
            insertPayment(connection, "PAY-BASE-WAIT", "wx_h5", now.minusMinutes(9));
            insertPayment(connection, "PAY-BACKOFF-WAIT", "wx_h5", now.minusMinutes(8));
            insertPayment(connection, "PAY-BACKOFF-DUE", "wx_h5", now.minusMinutes(7));
            insertPayment(connection, "PAY-MAX-WAIT", "alipay_h5", now.minusMinutes(6));
            insertPayment(connection, "PAY-MAX-DUE", "alipay_h5", now.minusMinutes(5));
            insertPayment(connection, "PAY-FRESH-ATTEMPT", "wx_h5", now.minusMinutes(20));
            insertPaymentAttempt(connection, "PAY-FRESH-ATTEMPT", now);
            insertPaymentWithoutAttempt(connection, "PAY-NO-ATTEMPT", "wx_h5", now.minusMinutes(20));

            insertQueryHistory(connection, "PAY-BASE-WAIT", now, 30);
            insertQueryHistory(connection, "PAY-BACKOFF-WAIT", now, 300, 90);
            insertQueryHistory(connection, "PAY-BACKOFF-DUE", now, 300, 130);
            insertQueryHistory(connection, "PAY-MAX-WAIT", now, 2100, 1800, 1500, 1200, 800);
            insertQueryHistory(connection, "PAY-MAX-DUE", now, 2100, 1800, 1500, 1200, 920);
            insertQueryHistory(connection, "PAY-OUTSIDE-MONITOR-WINDOW", now, 90000);
            insertRateLimitRejections(connection, now, 30, 3600, 7200, 90000);
            insertRefundRateLimitRejections(connection, now, 60, 1800, 90000);
            insertRefund(connection, "REF-NO-HISTORY", "wx_h5", "PROCESSING", now.minusMinutes(10), true);
            insertRefund(connection, "REF-BASE-WAIT", "wx_h5", "PROCESSING", now.minusMinutes(9), true);
            insertRefund(connection, "REF-BACKOFF-WAIT", "wx_h5", "PROCESSING", now.minusMinutes(8), true);
            insertRefund(connection, "REF-BACKOFF-DUE", "wx_h5", "PROCESSING", now.minusMinutes(7), true);
            insertRefund(connection, "REF-MAX-WAIT", "alipay_h5", "PROCESSING", now.minusMinutes(6), true);
            insertRefund(connection, "REF-MAX-DUE", "alipay_h5", "PROCESSING", now.minusMinutes(5), true);
            insertRefund(connection, "REF-FRESH", "wx_h5", "PROCESSING", now, true);
            insertRefund(connection, "REF-NO-TRACE", "wx_h5", "PROCESSING", now.minusMinutes(20), false);
            insertRefund(connection, "REF-SUCCESS", "wx_h5", "SUCCESS", now.minusMinutes(20), true);
            insertRefundQueryHistory(connection, "REF-BASE-WAIT", now, 30);
            insertRefundQueryHistory(connection, "REF-BACKOFF-WAIT", now, 300, 90);
            insertRefundQueryHistory(connection, "REF-BACKOFF-DUE", now, 300, 130);
            insertRefundQueryHistory(connection, "REF-MAX-WAIT", now, 2100, 1800, 1500, 1200, 800);
            insertRefundQueryHistory(connection, "REF-MAX-DUE", now, 2100, 1800, 1500, 1200, 920);
            statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                    + "source_alert_no, issue_type, alert_channel, ack_status, alert_status, "
                    + "provider_delivery_status, created_at) VALUES "
                    + "(NULL, '主动查单失败趋势', 'IN_APP_OUTBOX', '待确认', '已生成', NULL, "
                    + "TIMESTAMPADD(MINUTE, -45, NOW())),"
                    + "(NULL, '主动查单失败趋势', 'IN_APP_OUTBOX', '已确认', '已生成', NULL, "
                    + "TIMESTAMPADD(MINUTE, -120, NOW())),"
                    + "('SOURCE-1', '主动查单失败趋势', 'IN_APP_OUTBOX', '待确认', '派发成功', "
                    + "'ACCEPTED', TIMESTAMPADD(MINUTE, -65, NOW())),"
                    + "(NULL, '主动查单限流拒绝', 'IM', '待确认', '已生成', NULL, NOW()),"
                    + "(NULL, '异常告警派发', 'IN_APP_OUTBOX', '待确认', '部分失败', NULL, "
                    + "TIMESTAMPADD(MINUTE, -10, NOW())),"
                    + "(NULL, '异常告警派发', 'IN_APP_OUTBOX', '已确认', '派发失败', NULL, "
                    + "TIMESTAMPADD(MINUTE, -180, NOW())),"
                    + "('SOURCE-2', '异常告警派发', 'SMS', '待确认', '派发成功', 'DELIVERED', "
                    + "TIMESTAMPADD(MINUTE, -200, NOW())),"
                    + "('SOURCE-3', '异常告警派发', 'EMAIL', '已确认', '派发成功', 'ACCEPTED', "
                    + "TIMESTAMPADD(MINUTE, -300, NOW()))");
            statement.executeUpdate("UPDATE t_payment_notify_log SET notify_status = '查单失败' "
                    + "WHERE payment_order_id = 'PAY-BACKOFF-WAIT' AND id = ("
                    + "SELECT MAX(id) FROM t_payment_notify_log WHERE payment_order_id = 'PAY-BACKOFF-WAIT')");
            connection.commit();
        }
    }

    private void expireTaskLease(Connection connection, String taskCode) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE t_payment_task_lease SET lock_expires_at = ? WHERE task_code = ?")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(1)));
            statement.setString(2, taskCode);
            statement.executeUpdate();
        }
    }

    private PaymentIssueAlertLogEntity buildIssueAlertLog(String alertNo) {
        PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
        entity.setAlertNo(alertNo);
        entity.setGenerationKey("ISSUE_ACTIVE:ISSUE-IDEMPOTENT");
        entity.setIssueNo("ISSUE-IDEMPOTENT");
        entity.setPaymentOrderId("PAY-IDEMPOTENT");
        entity.setIssueType("待回调未收口");
        entity.setSeverity("P1");
        entity.setResponsibilityGroup("支付运营组");
        entity.setAlertChannel("IN_APP_OUTBOX");
        entity.setReceiver("支付运营");
        entity.setAlertStatus("已生成");
        entity.setAlertStatusType("warn");
        entity.setAckStatus("待确认");
        entity.setAckStatusType("warn");
        entity.setAlertContent("幂等测试告警");
        entity.setTriggeredBy("integration-test");
        return entity;
    }

    private int countAlertsByGenerationKey(Connection connection, String generationKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT COUNT(*) FROM t_payment_issue_alert_log WHERE generation_key = ?")) {
            statement.setString(1, generationKey);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getInt(1);
            }
        }
    }

    private String findAlertNoByGenerationKey(Connection connection, String generationKey) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT alert_no FROM t_payment_issue_alert_log WHERE generation_key = ?")) {
            statement.setString(1, generationKey);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getString(1);
            }
        }
    }

    private void insertPayment(
            Connection connection,
            String paymentOrderId,
            String channelCode,
            LocalDateTime createdAt)
            throws Exception {
        try (PreparedStatement paymentStatement = connection.prepareStatement(
                "INSERT INTO t_payment_order(payment_order_id, channel_code, status, created_at) "
                        + "VALUES (?, ?, ?, ?)");
             PreparedStatement prepayStatement = connection.prepareStatement(
                     "INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES (?, ?)");
             PreparedStatement attemptStatement = connection.prepareStatement(
                     "INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) "
                             + "VALUES (?, ?, ?)")) {
            paymentStatement.setString(1, paymentOrderId);
            paymentStatement.setString(2, channelCode);
            paymentStatement.setString(3, "WAIT_CALLBACK");
            paymentStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
            paymentStatement.executeUpdate();

            prepayStatement.setString(1, paymentOrderId);
            prepayStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
            prepayStatement.executeUpdate();

            attemptStatement.setString(1, paymentOrderId);
            attemptStatement.setString(2, "待回调");
            attemptStatement.setTimestamp(3, Timestamp.valueOf(createdAt));
            attemptStatement.executeUpdate();
        }
    }

    private void insertPaymentAttempt(
            Connection connection,
            String paymentOrderId,
            LocalDateTime createdAt) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) VALUES (?, ?, ?)")) {
            statement.setString(1, paymentOrderId);
            statement.setString(2, "渠道提交中");
            statement.setTimestamp(3, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        }
    }

    private void insertPaymentWithoutAttempt(
            Connection connection,
            String paymentOrderId,
            String channelCode,
            LocalDateTime createdAt) throws Exception {
        try (PreparedStatement paymentStatement = connection.prepareStatement(
                "INSERT INTO t_payment_order(payment_order_id, channel_code, status, created_at) "
                        + "VALUES (?, ?, ?, ?)");
             PreparedStatement prepayStatement = connection.prepareStatement(
                     "INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES (?, ?)")) {
            paymentStatement.setString(1, paymentOrderId);
            paymentStatement.setString(2, channelCode);
            paymentStatement.setString(3, "WAIT_CALLBACK");
            paymentStatement.setTimestamp(4, Timestamp.valueOf(createdAt));
            paymentStatement.executeUpdate();
            prepayStatement.setString(1, paymentOrderId);
            prepayStatement.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now().plusHours(1)));
            prepayStatement.executeUpdate();
        }
    }

    private void insertQueryHistory(
            Connection connection,
            String paymentOrderId,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_payment_notify_log(payment_order_id, notify_status, created_at) VALUES (?, ?, ?)")) {
            for (int seconds : secondsAgo) {
                statement.setString(1, paymentOrderId);
                statement.setString(2, "已查单");
                statement.setTimestamp(3, Timestamp.valueOf(now.minusSeconds(seconds)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertRateLimitRejections(
            Connection connection,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_payment_channel_query_rate_limit_log("
                        + "rejection_no, channel_code, payment_order_id, window_seconds, max_queries, rejected_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int index = 0; index < secondsAgo.length; index++) {
                statement.setString(1, "QRL-" + index);
                statement.setString(2, "wx_h5");
                statement.setString(3, "PAY-LIMITED-" + index);
                statement.setInt(4, 60);
                statement.setInt(5, 100);
                statement.setTimestamp(6, Timestamp.valueOf(now.minusSeconds(secondsAgo[index])));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertRefundRateLimitRejections(
            Connection connection,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_channel_query_rate_limit_log("
                        + "rejection_no, channel_code, refund_order_id, window_seconds, max_queries, rejected_at) "
                        + "VALUES (?, ?, ?, ?, ?, ?)")) {
            for (int index = 0; index < secondsAgo.length; index++) {
                statement.setString(1, "RQRL-" + index);
                statement.setString(2, "wx_h5");
                statement.setString(3, "REF-LIMITED-" + index);
                statement.setInt(4, 60);
                statement.setInt(5, 100);
                statement.setTimestamp(6, Timestamp.valueOf(now.minusSeconds(secondsAgo[index])));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertRefund(
            Connection connection,
            String refundOrderId,
            String channelCode,
            String status,
            LocalDateTime submittedAt,
            boolean withTrace) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_order(refund_order_id, channel_code, channel_refund_no, "
                        + "channel_submitted_at, status, applied_at) VALUES (?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, refundOrderId);
            statement.setString(2, withTrace ? channelCode : null);
            statement.setString(3, withTrace ? "CHANNEL-" + refundOrderId : null);
            statement.setTimestamp(4, withTrace ? Timestamp.valueOf(submittedAt) : null);
            statement.setString(5, status);
            statement.setTimestamp(6, Timestamp.valueOf(submittedAt));
            statement.executeUpdate();
        }
    }

    private void insertRefundQueryHistory(
            Connection connection,
            String refundOrderId,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_operation_log(refund_order_id, action_code, created_at) VALUES (?, ?, ?)")) {
            for (int seconds : secondsAgo) {
                statement.setString(1, refundOrderId);
                statement.setString(2, "CHANNEL_QUERY");
                statement.setTimestamp(3, Timestamp.valueOf(now.minusSeconds(seconds)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
