package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueAlertLogRowDTO;
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
public class PaymentTaskCenterMapperIntegrationTest {

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

            assertEquals(Arrays.asList(
                    "REF-UNKNOWN-NO-HISTORY",
                    "REF-NO-HISTORY",
                    "REF-UNKNOWN-BACKOFF-DUE",
                    "REF-BACKOFF-DUE",
                    "REF-MAX-DUE"),
                    automaticCandidates);
            assertEquals(Arrays.asList(
                    "REF-UNKNOWN-NO-HISTORY", "REF-UNKNOWN-BACKOFF-DUE", "REF-MAX-DUE"),
                    channelLimitedAutomaticCandidates);
            assertEquals(Arrays.asList(
                    "REF-UNKNOWN-NO-HISTORY",
                    "REF-NO-HISTORY",
                    "REF-UNKNOWN-BACKOFF-WAIT",
                    "REF-BASE-WAIT",
                    "REF-UNKNOWN-BACKOFF-DUE",
                    "REF-BACKOFF-WAIT",
                    "REF-BACKOFF-DUE",
                    "REF-MAX-WAIT",
                    "REF-MAX-DUE"), manualCandidates);
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-FRESH"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-NO-TRACE"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-SUCCESS"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-UNKNOWN-FRESH"));
            org.junit.jupiter.api.Assertions.assertFalse(manualCandidates.contains("REF-UNKNOWN-PENDING"));
            assertEquals(9, mapper.countPendingRefundQueryBacklog(30));
            assertEquals(5, mapper.countDuePendingRefundQueries(30, 60, 2, 900));
        }
    }

    @Test
    void shouldNotCarryPreviousAttemptQueryBackoffIntoNewRefundAttempt() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             PreparedStatement statement = session.getConnection().prepareStatement(
                     "INSERT INTO t_refund_attempt(refund_attempt_no, refund_order_id, channel_code, "
                             + "channel_refund_no, attempt_status, attempt_status_type, submitted_at, "
                             + "completed_at, created_at) "
                             + "VALUES ('RFA-REF-BASE-WAIT-NEW', 'REF-BASE-WAIT', 'wx_h5', "
                             + "'CHANNEL-REF-BASE-WAIT-NEW', 'ACCEPTED', 'warn', ?, NULL, "
                             + "(SELECT MAX(created_at) FROM t_refund_operation_log "
                             + "WHERE refund_order_id = 'REF-BASE-WAIT'))")) {
            statement.setTimestamp(1, Timestamp.valueOf(LocalDateTime.now().minusSeconds(10)));
            statement.executeUpdate();
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            List<String> candidates = mapper.findDuePendingRefundOrderIdsForQuery(
                    0, 60, 2, 900, 20, 100);

            org.junit.jupiter.api.Assertions.assertTrue(candidates.contains("REF-BASE-WAIT"));
        }
    }

    @Test
    void shouldSeparateDueAndBackingOffExpiryCloseBacklogs() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(2, mapper.countDueExpiredPayments());
            assertEquals(1, mapper.countBackingOffExpiredPayments());
        }
    }

    @Test
    void shouldSummarizeRecentPaymentAndRefundQueryTrendWithinLookbackWindow() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(36, mapper.countRecentChannelQueries(24));
            assertEquals(4, mapper.countRecentChannelQueryFailures(24));
            assertEquals(5, mapper.countRecentChannelQueryRateLimitRejections(24));
            List<PaymentQueryTrendPointDTO> trends = mapper.findRecentChannelQueryTrends(24);

            assertEquals(36, trends.stream().mapToInt(PaymentQueryTrendPointDTO::getQueryCount).sum());
            assertEquals(4, trends.stream().mapToInt(PaymentQueryTrendPointDTO::getFailureCount).sum());
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
    void shouldLimitPendingOutboxAlertsAndKeepOldestFirst() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log SET ack_status = '已确认' "
                        + "WHERE alert_channel = 'IN_APP_OUTBOX'");
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, issue_no, issue_type, severity, alert_channel, ack_status, alert_status, "
                        + "created_at) VALUES "
                        + "('PIA-DISPATCH-OLDEST', 'ISSUE-DISPATCH-1', '待回调未收口', 'P1', "
                        + "'IN_APP_OUTBOX', '待确认', '已生成', TIMESTAMPADD(MINUTE, -3, NOW())),"
                        + "('PIA-DISPATCH-SECOND', 'ISSUE-DISPATCH-2', '待回调未收口', 'P1', "
                        + "'IN_APP_OUTBOX', '待确认', '部分失败', TIMESTAMPADD(MINUTE, -2, NOW())),"
                        + "('PIA-DISPATCH-LATER', 'ISSUE-DISPATCH-3', '待回调未收口', 'P1', "
                        + "'IN_APP_OUTBOX', '待确认', '派发失败', TIMESTAMPADD(MINUTE, -1, NOW()))");
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, source_alert_no, issue_no, issue_type, severity, alert_channel, "
                        + "ack_status, alert_status, created_at) VALUES "
                        + "('PIA-DISPATCH-NESTED', 'PIA-DISPATCH-PARENT', 'ISSUE-DISPATCH-NESTED', "
                        + "'待回调未收口', 'P1', 'IN_APP_OUTBOX', '待确认', '已生成', "
                        + "TIMESTAMPADD(MINUTE, -4, NOW()))");
            }

            List<PaymentIssueAlertDispatchItemDTO> candidates = mapper.findPendingOutboxAlerts(2);

            assertEquals(2, candidates.size());
            assertEquals(Arrays.asList("PIA-DISPATCH-OLDEST", "PIA-DISPATCH-SECOND"),
                    candidates.stream()
                            .map(PaymentIssueAlertDispatchItemDTO::getAlertNo)
                            .collect(java.util.stream.Collectors.toList()));
            org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(
                    mapper.lockPendingOutboxAlert("PIA-DISPATCH-NESTED", "ISSUE-DISPATCH-NESTED")));
            org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(
                    mapper.lockPendingOutboxAlert("PIA-DISPATCH-OLDEST", "ISSUE-DISPATCH-WRONG")));
            org.junit.jupiter.api.Assertions.assertTrue(Boolean.TRUE.equals(
                    mapper.lockPendingOutboxAlert("PIA-DISPATCH-OLDEST", "ISSUE-DISPATCH-1")));
            PaymentIssueAlertLogEntity update = new PaymentIssueAlertLogEntity();
            update.setAlertNo("PIA-DISPATCH-OLDEST");
            update.setIssueNo("ISSUE-DISPATCH-1");
            update.setAlertStatus("已派发");
            update.setAlertStatusType("success");
            update.setAckStatus("待确认");
            update.setAckStatusType("warn");
            update.setTriggeredBy("payment-core-admin");
            assertEquals(1, mapper.updateIssueAlertDeliveryStatus(update));
            org.junit.jupiter.api.Assertions.assertFalse(Boolean.TRUE.equals(
                    mapper.lockPendingOutboxAlert("PIA-DISPATCH-OLDEST", "ISSUE-DISPATCH-1")));
            update.setAlertNo("PIA-DISPATCH-NESTED");
            update.setIssueNo("ISSUE-DISPATCH-NESTED");
            assertEquals(0, mapper.updateIssueAlertDeliveryStatus(update));
        }
    }

    @Test
    void shouldOnlyUseDeliveryHistoryBelongingToMatchingTopLevelSource() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, source_alert_no, issue_no, alert_channel, alert_status, ack_status, "
                        + "provider_delivery_status, created_at) "
                        + "VALUES "
                        + "('PIA-HISTORY-SOURCE', NULL, 'ISSUE-HISTORY', 'IN_APP_OUTBOX', "
                        + "'已生成', '已确认', NULL, TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-HISTORY-SUCCESS', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', 'IM', "
                        + "'已派发', '待确认', 'ACCEPTED', TIMESTAMPADD(MINUTE, -5, NOW())),"
                        + "('PIA-HISTORY-FAILED', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', 'IM', "
                        + "'派发失败', '无需回执', 'FAILED', TIMESTAMPADD(MINUTE, -4, NOW())),"
                        + "('PIA-HISTORY-WRONG-ISSUE', 'PIA-HISTORY-SOURCE', 'ISSUE-OTHER', 'IM', "
                        + "'派发失败', '无需回执', 'FAILED', TIMESTAMPADD(MINUTE, -1, NOW())),"
                        + "('PIA-HISTORY-WRONG-SUCCESS', 'PIA-HISTORY-SOURCE', 'ISSUE-OTHER', 'SMS', "
                        + "'已派发', '待确认', 'ACCEPTED', NOW()),"
                        + "('PIA-HISTORY-NO-EVIDENCE', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', 'SMS', "
                        + "'已派发', '待确认', NULL, NOW()),"
                        + "('PIA-HISTORY-FAILED-EVIDENCE', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', 'EMAIL', "
                        + "'已派发', '待确认', 'FAILED', NOW()),"
                        + "('PIA-HISTORY-DELIVERED', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', 'VOICE', "
                        + "'已派发', '待确认', 'DELIVERED', NOW()),"
                        + "('PIA-HISTORY-ORPHAN', 'PIA-HISTORY-MISSING', 'ISSUE-HISTORY', 'EMAIL', "
                        + "'已派发', '待确认', 'ACCEPTED', NOW()),"
                        + "('PIA-HISTORY-NESTED', 'PIA-HISTORY-SOURCE', 'ISSUE-HISTORY', "
                        + "'IN_APP_OUTBOX', '已生成', '待确认', NULL, NOW()),"
                        + "('PIA-HISTORY-NESTED-DELIVERY', 'PIA-HISTORY-NESTED', 'ISSUE-HISTORY', 'EMAIL', "
                        + "'已派发', '待确认', 'ACCEPTED', NOW())");
            }

            org.junit.jupiter.api.Assertions.assertTrue(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-SOURCE", "IM"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-SOURCE", "SMS"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-SOURCE", "EMAIL"));
            org.junit.jupiter.api.Assertions.assertTrue(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-SOURCE", "VOICE"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-MISSING", "EMAIL"));
            org.junit.jupiter.api.Assertions.assertFalse(
                    mapper.hasSuccessfulIssueAlertChannelDelivery("PIA-HISTORY-NESTED", "EMAIL"));
            assertEquals(1, mapper.countFailedIssueAlertChannelDeliveries("PIA-HISTORY-SOURCE", "IM"));
            PaymentIssueAlertLogEntity latest =
                    mapper.findLatestIssueAlertChannelDeliveryLog("PIA-HISTORY-SOURCE", "IM");
            assertEquals("PIA-HISTORY-FAILED", latest.getAlertNo());
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findLatestIssueAlertChannelDeliveryLog("PIA-HISTORY-MISSING", "EMAIL"));
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findLatestIssueAlertChannelDeliveryLog("PIA-HISTORY-NESTED", "EMAIL"));
        }
    }

    @Test
    void shouldOnlyCountActualProviderAttemptsForRetryAndCircuitBreaker() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, source_alert_no, issue_no, alert_channel, alert_status, ack_status, "
                        + "provider_code, provider_delivery_status, created_at) VALUES "
                        + "('PIA-ATTEMPT-SOURCE', NULL, 'ISSUE-ATTEMPT', 'IN_APP_OUTBOX', "
                        + "'派发失败', '待确认', NULL, NULL, TIMESTAMPADD(MINUTE, -20, NOW())),"
                        + "('PIA-ATTEMPT-FAILED', 'PIA-ATTEMPT-SOURCE', 'ISSUE-ATTEMPT', 'IM', "
                        + "'派发失败', '无需回执', 'ALERT_IM_PRIMARY', 'FAILED', "
                        + "TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-ATTEMPT-EXCEPTION', 'PIA-ATTEMPT-SOURCE', 'ISSUE-ATTEMPT', 'IM', "
                        + "'派发失败', '无需回执', 'ALERT_IM_PRIMARY', 'SEND_EXCEPTION', "
                        + "TIMESTAMPADD(MINUTE, -9, NOW())),"
                        + "('PIA-ATTEMPT-RATE-LIMIT', 'PIA-ATTEMPT-SOURCE', 'ISSUE-ATTEMPT', 'IM', "
                        + "'派发失败', '无需回执', 'ALERT_IM_PRIMARY', 'RATE_LIMITED', "
                        + "TIMESTAMPADD(MINUTE, -3, NOW())),"
                        + "('PIA-ATTEMPT-COOLDOWN', 'PIA-ATTEMPT-SOURCE', 'ISSUE-ATTEMPT', 'IM', "
                        + "'派发失败', '无需回执', 'ALERT_IM_PRIMARY', 'RETRY_COOLDOWN_ACTIVE', "
                        + "TIMESTAMPADD(MINUTE, -2, NOW())),"
                        + "('PIA-ATTEMPT-CIRCUIT', 'PIA-ATTEMPT-SOURCE', 'ISSUE-ATTEMPT', 'IM', "
                        + "'派发失败', '无需回执', 'ALERT_IM_PRIMARY', 'PROVIDER_CIRCUIT_OPEN', "
                        + "TIMESTAMPADD(MINUTE, -1, NOW()))");
            }

            assertEquals(2, mapper.countFailedIssueAlertChannelDeliveries("PIA-ATTEMPT-SOURCE", "IM"));
            assertEquals(Arrays.asList("SEND_EXCEPTION", "FAILED"),
                    mapper.findRecentIssueAlertProviderDeliveryStatusesSince(
                            "ALERT_IM_PRIMARY", "IM", "2000-01-01 00:00:00", 3));
            PaymentIssueAlertLogEntity latest =
                    mapper.findLatestIssueAlertChannelDeliveryLog("PIA-ATTEMPT-SOURCE", "IM");
            assertEquals("PIA-ATTEMPT-EXCEPTION", latest.getAlertNo());
        }
    }

    @Test
    void shouldReturnOnlyRecentConsecutiveProviderAttemptEvidence() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, source_alert_no, issue_no, alert_channel, alert_status, ack_status, "
                        + "provider_code, provider_delivery_status, created_at) VALUES "
                        + "('PIA-CIRCUIT-RECOVERED-SOURCE', NULL, 'ISSUE-CIRCUIT-RECOVERED', "
                        + "'IN_APP_OUTBOX', '派发失败', '已确认', NULL, NULL, "
                        + "TIMESTAMPADD(MINUTE, -20, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-F1', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '派发失败', '无需回执', "
                        + "'ALERT_IM_RECOVERED', 'FAILED', TIMESTAMPADD(MINUTE, -7, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-F2', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '派发失败', '无需回执', "
                        + "'ALERT_IM_RECOVERED', 'SEND_EXCEPTION', TIMESTAMPADD(MINUTE, -6, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-F3', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '派发失败', '无需回执', "
                        + "'ALERT_IM_RECOVERED', 'FAILED', TIMESTAMPADD(MINUTE, -5, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-SUCCESS', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '已派发', '待确认', "
                        + "'ALERT_IM_RECOVERED', 'ACCEPTED', TIMESTAMPADD(MINUTE, -4, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-F4', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '派发失败', '无需回执', "
                        + "'ALERT_IM_RECOVERED', 'FAILED', TIMESTAMPADD(MINUTE, -3, NOW())),"
                        + "('PIA-CIRCUIT-RECOVERED-F5', 'PIA-CIRCUIT-RECOVERED-SOURCE', "
                        + "'ISSUE-CIRCUIT-RECOVERED', 'IM', '派发失败', '无需回执', "
                        + "'ALERT_IM_RECOVERED', 'SEND_EXCEPTION', TIMESTAMPADD(MINUTE, -2, NOW())),"
                        + "('PIA-CIRCUIT-OPEN-SOURCE', NULL, 'ISSUE-CIRCUIT-OPEN', "
                        + "'IN_APP_OUTBOX', '派发失败', '已确认', NULL, NULL, "
                        + "TIMESTAMPADD(MINUTE, -20, NOW())),"
                        + "('PIA-CIRCUIT-OPEN-F1', 'PIA-CIRCUIT-OPEN-SOURCE', 'ISSUE-CIRCUIT-OPEN', "
                        + "'IM', '派发失败', '无需回执', 'ALERT_IM_OPEN', 'FAILED', "
                        + "TIMESTAMPADD(MINUTE, -3, NOW())),"
                        + "('PIA-CIRCUIT-OPEN-F2', 'PIA-CIRCUIT-OPEN-SOURCE', 'ISSUE-CIRCUIT-OPEN', "
                        + "'IM', '派发失败', '无需回执', 'ALERT_IM_OPEN', 'SEND_EXCEPTION', "
                        + "TIMESTAMPADD(MINUTE, -2, NOW())),"
                        + "('PIA-CIRCUIT-OPEN-F3', 'PIA-CIRCUIT-OPEN-SOURCE', 'ISSUE-CIRCUIT-OPEN', "
                        + "'IM', '派发失败', '无需回执', 'ALERT_IM_OPEN', 'FAILED', "
                        + "TIMESTAMPADD(MINUTE, -1, NOW())),"
                        + "('PIA-CIRCUIT-OPEN-ORPHAN', 'PIA-CIRCUIT-MISSING', 'ISSUE-CIRCUIT-OPEN', "
                        + "'IM', '派发失败', '无需回执', 'ALERT_IM_OPEN', 'SEND_EXCEPTION', NOW()),"
                        + "('PIA-CIRCUIT-OPEN-WRONG-ISSUE', 'PIA-CIRCUIT-OPEN-SOURCE', 'ISSUE-OTHER', "
                        + "'IM', '派发失败', '无需回执', 'ALERT_IM_OPEN', 'SEND_EXCEPTION', NOW()),"
                        + "('PIA-CIRCUIT-OPEN-INCONSISTENT', 'PIA-CIRCUIT-OPEN-SOURCE', "
                        + "'ISSUE-CIRCUIT-OPEN', 'IM', '已派发', '待确认', 'ALERT_IM_OPEN', "
                        + "'FAILED', NOW())");
            }

            assertEquals(Arrays.asList("SEND_EXCEPTION", "FAILED", "ACCEPTED"),
                    mapper.findRecentIssueAlertProviderDeliveryStatusesSince(
                            "ALERT_IM_RECOVERED", "IM", "2000-01-01 00:00:00", 3));
            assertEquals(Arrays.asList("FAILED", "SEND_EXCEPTION", "FAILED"),
                    mapper.findRecentIssueAlertProviderDeliveryStatusesSince(
                            "ALERT_IM_OPEN", "IM", "2000-01-01 00:00:00", 3));
        }
    }

    @Test
    void shouldCountOnlyActionableIssueAlertDeliveryBacklogs() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, source_alert_no, issue_type, alert_channel, ack_status, alert_status, "
                        + "provider_delivery_status, receipt_query_count, last_receipt_query_at, "
                        + "next_receipt_query_at, receipt_query_result, created_at) VALUES "
                        + "('PIA-METRIC-DUE', 'SOURCE-METRIC-DUE', '异常告警派发', 'SMS', '待确认', "
                        + "'已派发', 'ACCEPTED', 0, NULL, NULL, NULL, TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-METRIC-BACKOFF', 'SOURCE-METRIC-BACKOFF', '异常告警派发', 'EMAIL', "
                        + "'待确认', '已派发', 'ACCEPTED', 1, NOW(), TIMESTAMPADD(MINUTE, 10, NOW()), "
                        + "'ACCEPTED', TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-METRIC-QUERYING', 'SOURCE-METRIC-QUERYING', '异常告警派发', 'IM', "
                        + "'待确认', '已派发', 'ACCEPTED', 1, NOW(), TIMESTAMPADD(SECOND, 30, NOW()), "
                        + "'QUERYING', TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-METRIC-EXHAUSTED', 'SOURCE-METRIC-EXHAUSTED', '异常告警派发', 'SMS', "
                        + "'待确认', '已派发', 'ACCEPTED', 10, NOW(), NULL, 'FAILED', "
                        + "TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-METRIC-STALE', 'SOURCE-METRIC-STALE', '异常告警派发', 'EMAIL', "
                        + "'待确认', '已派发', 'ACCEPTED', 2, TIMESTAMPADD(MINUTE, -2, NOW()), "
                        + "TIMESTAMPADD(MINUTE, -1, NOW()), 'QUERYING', "
                        + "TIMESTAMPADD(MINUTE, -10, NOW())),"
                        + "('PIA-METRIC-INITIAL', 'SOURCE-METRIC-INITIAL', '异常告警派发', 'SMS', "
                        + "'待确认', '已派发', 'ACCEPTED', 0, NULL, NULL, NULL, NOW()),"
                        + "('PIA-METRIC-INITIAL-BACKOFF', 'SOURCE-METRIC-INITIAL-BACKOFF', "
                        + "'异常告警派发', 'EMAIL', '待确认', '已派发', 'ACCEPTED', 0, NULL, "
                        + "TIMESTAMPADD(MINUTE, 10, NOW()), NULL, NOW())");
            }

            assertEquals(2, mapper.countPendingIssueAlertDispatches());
            assertEquals(1, mapper.countFailedIssueAlertDispatches());
            assertEquals(45, mapper.findOldestPendingIssueAlertDispatchMinutes());
            assertEquals(7, mapper.countPendingIssueAlertReceipts());
            assertEquals(2, mapper.countDueIssueAlertReceipts(60, 10, 30));
            assertEquals(1, mapper.countBackingOffIssueAlertReceipts(60, 10, 30));
            assertEquals(1, mapper.countQueryingIssueAlertReceipts(30));
            assertEquals(1, mapper.countExhaustedIssueAlertReceipts(10, 30));
            assertEquals(10, mapper.findOldestPendingIssueAlertReceiptMinutes());

            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueAlertLogQueryDTO query = new PaymentIssueAlertLogQueryDTO();
            query.setAlertChannel("IN_APP_OUTBOX");
            query.setAlertStatus("PENDING_DISPATCH");
            query.setAckStatus("待确认");
            assertEquals(2, issueMapper.countAlertLogs(query));
            assertEquals(Arrays.asList("部分失败", "已生成"),
                    issueMapper.findAlertLogs(query).stream()
                            .map(PaymentIssueAlertLogRowDTO::getAlertStatus)
                            .collect(java.util.stream.Collectors.toList()));

            query.setAlertChannel("全部");
            query.setAlertStatus("全部");
            query.setAckStatus("全部");
            query.setReceiptQueryMinAgeSeconds(60);
            query.setReceiptQueryMaxAttempts(10);
            query.setReceiptQueryClaimTimeoutSeconds(30);
            query.setReceiptQueryState("PENDING_RECEIPT");
            assertEquals(7, issueMapper.countAlertLogs(query));
            assertEquals(7, issueMapper.findAlertLogs(query).size());
            query.setReceiptQueryState("EXHAUSTED");
            assertEquals(1, issueMapper.countAlertLogs(query));
            PaymentIssueAlertLogRowDTO exhausted = issueMapper.findAlertLogs(query).get(0);
            assertEquals("PIA-METRIC-EXHAUSTED", exhausted.getAlertNo());
            assertEquals("EXHAUSTED", exhausted.getReceiptQueryState());
            assertEquals(Integer.valueOf(10), exhausted.getReceiptQueryCount());
            assertEquals("FAILED", exhausted.getReceiptQueryResult());

            query.setReceiptQueryState("DUE");
            assertEquals(2, issueMapper.countAlertLogs(query));
            query.setReceiptQueryState("BACKING_OFF");
            assertEquals(1, issueMapper.countAlertLogs(query));
            query.setReceiptQueryState("QUERYING");
            assertEquals(1, issueMapper.countAlertLogs(query));
            query.setReceiptQueryState("INITIAL_WAIT");
            assertEquals(2, issueMapper.countAlertLogs(query));
        }
    }

    @Test
    void shouldCountUnknownRefundSubmitAsOverdueP1Issue() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);

            assertEquals(5, mapper.countUnknownRefundSubmitEvents());
            assertEquals(1, mapper.countOverduePaymentIssues());
            List<PaymentIssueAlertCandidateDTO> candidates = mapper.findOverdueIssueAlertCandidates(100);
            assertEquals(1, candidates.size());
            org.junit.jupiter.api.Assertions.assertTrue(candidates.stream().allMatch(candidate ->
                    "退款提交结果待核查".equals(candidate.getIssueType())
                            && "账务清结算联动组".equals(candidate.getResponsibilityGroup())
                            && "账清结算联动值班".equals(candidate.getReceiver())));
        }
    }

    @Test
    void shouldLimitOverdueIssueAlertCandidatesAndKeepOldestFirst() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_order "
                        + "SET created_at = TIMESTAMPADD(MINUTE, -61, NOW()) "
                        + "WHERE payment_order_id = 'PAY-NO-HISTORY'");
            }

            List<PaymentIssueAlertCandidateDTO> candidates = mapper.findOverdueIssueAlertCandidates(1);

            assertEquals(1, candidates.size());
            assertEquals("ISSUE-WAIT-PAY-NO-HISTORY", candidates.get(0).getIssueNo());
        }
    }

    @Test
    void shouldLimitUnacknowledgedEscalationCandidatesAndKeepOldestFirst() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log SET ack_status = '已确认' "
                        + "WHERE alert_channel = 'IN_APP_OUTBOX'");
                statement.executeUpdate("INSERT INTO t_payment_issue_alert_log("
                        + "alert_no, issue_no, issue_type, severity, responsibility_group, receiver, "
                        + "alert_channel, ack_status, alert_status, alert_content, created_at) VALUES "
                        + "('PIA-ESCALATE-OLDEST', 'ISSUE-ESCALATE-1', '待回调未收口', 'P1', "
                        + "'支付运营组', '支付运营', 'IN_APP_OUTBOX', '待确认', '已生成', "
                        + "'最早待升级告警', TIMESTAMPADD(MINUTE, -40, NOW())),"
                        + "('PIA-ESCALATE-LATER', 'ISSUE-ESCALATE-2', '待回调未收口', 'P1', "
                        + "'支付运营组', '支付运营', 'IN_APP_OUTBOX', '待确认', '已生成', "
                        + "'较晚待升级告警', TIMESTAMPADD(MINUTE, -35, NOW()))");
            }

            List<PaymentIssueAlertCandidateDTO> candidates =
                    mapper.findUnacknowledgedIssueAlertEscalationCandidates(1);

            assertEquals(1, candidates.size());
            assertEquals("PIA-ESCALATE-OLDEST", candidates.get(0).getSourceAlertNo());
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
    void shouldPersistConfirmedProviderReceiptEvidence() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            PaymentIssueAlertLogEntity accepted = buildIssueAlertLog("PIA-RECEIPT-UPDATE");
            accepted.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-UPDATE");
            accepted.setSourceAlertNo("PIA-OUTBOX-RECEIPT-UPDATE");
            accepted.setAlertChannel("IM");
            accepted.setAlertStatus("已派发");
            accepted.setProviderReceiptSnapshot("HTTP_RESPONSE:{\"status\":\"ACCEPTED\"}");
            accepted.setProviderReceiptNo("PROVIDER-RECEIPT-001");
            accepted.setProviderDeliveryStatus("ACCEPTED");
            accepted.setProviderDeliveryMessage("供应商已受理");
            PaymentIssueAlertLogEntity source = buildIssueAlertLog("PIA-OUTBOX-RECEIPT-UPDATE");
            source.setGenerationKey("ISSUE_ACTIVE:PIA-OUTBOX-RECEIPT-UPDATE");
            assertEquals(1, mapper.insertIssueAlertLog(source));
            assertEquals(1, mapper.insertIssueAlertLog(accepted));
            assertEquals(1, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 0, true, 10, 30));

            PaymentIssueAlertLogEntity update = new PaymentIssueAlertLogEntity();
            update.setAlertNo(accepted.getAlertNo());
            update.setSourceAlertNo(accepted.getSourceAlertNo());
            update.setIssueNo(accepted.getIssueNo());
            update.setProviderReceiptSnapshot("HTTP_RESPONSE:{\"status\":\"DELIVERED\"}");
            update.setProviderReceiptNo("PROVIDER-RECEIPT-001");
            update.setProviderDeliveryStatus("DELIVERED");
            update.setProviderDeliveryMessage("供应商查询确认送达");
            update.setAckStatus("已确认");
            update.setAckStatusType("success");
            update.setAckOperator("payment-core-admin");
            update.setTriggeredBy("integration-test");
            update.setReceiptQueryCount(0);
            assertEquals(0, mapper.updateIssueAlertProviderReceipt(update));
            update.setReceiptQueryCount(1);
            update.setSourceAlertNo("PIA-OUTBOX-MISSING");
            assertEquals(0, mapper.updateIssueAlertProviderReceipt(update));
            update.setSourceAlertNo(accepted.getSourceAlertNo());
            assertEquals(1, mapper.updateIssueAlertProviderReceipt(update));
            PaymentIssueAlertLogEntity persisted = findIssueAlertReceipt(
                    session.getConnection(), accepted.getAlertNo());
            assertEquals("HTTP_RESPONSE:{\"status\":\"DELIVERED\"}",
                    persisted.getProviderReceiptSnapshot());
            assertEquals("PROVIDER-RECEIPT-001", persisted.getProviderReceiptNo());
            assertEquals("DELIVERED", persisted.getProviderDeliveryStatus());
            assertEquals("供应商查询确认送达", persisted.getProviderDeliveryMessage());
            assertEquals("已确认", persisted.getAckStatus());
            assertEquals("success", persisted.getAckStatusType());
            assertEquals("payment-core-admin", persisted.getAckOperator());
            assertEquals("integration-test", persisted.getTriggeredBy());
            assertEquals(Integer.valueOf(1), persisted.getReceiptQueryCount());
            assertEquals("DELIVERED", persisted.getReceiptQueryResult());
        }
    }

    @Test
    void shouldAcknowledgeOnlyMatchingTopLevelSourceAlert() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            PaymentIssueAlertLogEntity matchingSource = buildIssueAlertLog("PIA-SOURCE-MATCH");
            matchingSource.setGenerationKey("ISSUE_ACTIVE:PIA-SOURCE-MATCH");
            PaymentIssueAlertLogEntity otherIssueSource = buildIssueAlertLog("PIA-SOURCE-OTHER");
            otherIssueSource.setGenerationKey("ISSUE_ACTIVE:PIA-SOURCE-OTHER");
            otherIssueSource.setIssueNo("ISSUE-OTHER");
            PaymentIssueAlertLogEntity nestedOutbox = buildIssueAlertLog("PIA-SOURCE-NESTED");
            nestedOutbox.setGenerationKey("ISSUE_ACTIVE:PIA-SOURCE-NESTED");
            nestedOutbox.setSourceAlertNo("PIA-SOURCE-PARENT");
            assertEquals(1, mapper.insertIssueAlertLog(matchingSource));
            assertEquals(1, mapper.insertIssueAlertLog(otherIssueSource));
            assertEquals(1, mapper.insertIssueAlertLog(nestedOutbox));

            PaymentIssueAlertLogEntity update = new PaymentIssueAlertLogEntity();
            update.setAlertNo(matchingSource.getAlertNo());
            update.setIssueNo(matchingSource.getIssueNo());
            update.setAckStatus("已确认");
            update.setAckStatusType("success");
            update.setAckOperator("payment-core-admin");
            update.setTriggeredBy("integration-test");
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingSourceIssueAlertAcknowledged(
                    matchingSource.getAlertNo(), matchingSource.getIssueNo()));
            assertEquals(1, mapper.updateSourceIssueAlertAcknowledgement(update));

            update.setAlertNo(otherIssueSource.getAlertNo());
            update.setIssueNo(matchingSource.getIssueNo());
            assertEquals(0, mapper.updateSourceIssueAlertAcknowledgement(update));
            update.setAlertNo(nestedOutbox.getAlertNo());
            assertEquals(0, mapper.updateSourceIssueAlertAcknowledgement(update));

            assertEquals("已确认", findIssueAlertReceipt(
                    session.getConnection(), matchingSource.getAlertNo()).getAckStatus());
            assertEquals("待确认", findIssueAlertReceipt(
                    session.getConnection(), otherIssueSource.getAlertNo()).getAckStatus());
            assertEquals("待确认", findIssueAlertReceipt(
                    session.getConnection(), nestedOutbox.getAlertNo()).getAckStatus());
            org.junit.jupiter.api.Assertions.assertTrue(mapper.isMatchingSourceIssueAlertAcknowledged(
                    matchingSource.getAlertNo(), matchingSource.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingSourceIssueAlertAcknowledged(
                    otherIssueSource.getAlertNo(), matchingSource.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingSourceIssueAlertAcknowledged(
                    nestedOutbox.getAlertNo(), nestedOutbox.getIssueNo()));
        }
    }

    @Test
    void shouldSelectClaimBackoffAndExhaustAutomaticReceiptQueries() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentTaskCenterMapper mapper = session.getMapper(PaymentTaskCenterMapper.class);
            PaymentIssueAlertLogEntity accepted = buildIssueAlertLog("PIA-RECEIPT-RETRY");
            accepted.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-RETRY");
            accepted.setSourceAlertNo("PIA-OUTBOX-RECEIPT-RETRY");
            accepted.setAlertChannel("SMS");
            accepted.setAlertStatus("已派发");
            accepted.setProviderDeliveryStatus("ACCEPTED");
            PaymentIssueAlertLogEntity source = buildIssueAlertLog("PIA-OUTBOX-RECEIPT-RETRY");
            source.setGenerationKey("ISSUE_ACTIVE:PIA-OUTBOX-RECEIPT-RETRY");
            assertEquals(1, mapper.insertIssueAlertLog(source));
            assertEquals(1, mapper.insertIssueAlertLog(accepted));
            PaymentIssueAlertLogEntity orphan = buildIssueAlertLog("PIA-RECEIPT-ORPHAN");
            orphan.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-ORPHAN");
            orphan.setAlertChannel("EMAIL");
            orphan.setAlertStatus("已派发");
            orphan.setProviderDeliveryStatus("ACCEPTED");
            assertEquals(1, mapper.insertIssueAlertLog(orphan));
            PaymentIssueAlertLogEntity wrongIssueSource = buildIssueAlertLog("PIA-OUTBOX-WRONG-ISSUE");
            wrongIssueSource.setGenerationKey("ISSUE_ACTIVE:PIA-OUTBOX-WRONG-ISSUE");
            wrongIssueSource.setIssueNo("ISSUE-OTHER");
            assertEquals(1, mapper.insertIssueAlertLog(wrongIssueSource));
            PaymentIssueAlertLogEntity wrongIssueDelivery = buildIssueAlertLog("PIA-RECEIPT-WRONG-ISSUE");
            wrongIssueDelivery.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-WRONG-ISSUE");
            wrongIssueDelivery.setSourceAlertNo(wrongIssueSource.getAlertNo());
            wrongIssueDelivery.setAlertChannel("IM");
            wrongIssueDelivery.setAlertStatus("已派发");
            wrongIssueDelivery.setProviderDeliveryStatus("ACCEPTED");
            assertEquals(1, mapper.insertIssueAlertLog(wrongIssueDelivery));
            PaymentIssueAlertLogEntity nestedSource = buildIssueAlertLog("PIA-OUTBOX-NESTED-SOURCE");
            nestedSource.setGenerationKey("ISSUE_DELIVERY:PIA-OUTBOX-NESTED-SOURCE");
            nestedSource.setSourceAlertNo(source.getAlertNo());
            assertEquals(1, mapper.insertIssueAlertLog(nestedSource));
            PaymentIssueAlertLogEntity nestedSourceDelivery = buildIssueAlertLog("PIA-RECEIPT-NESTED-SOURCE");
            nestedSourceDelivery.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-NESTED-SOURCE");
            nestedSourceDelivery.setSourceAlertNo(nestedSource.getAlertNo());
            nestedSourceDelivery.setAlertChannel("EMAIL");
            nestedSourceDelivery.setAlertStatus("已派发");
            nestedSourceDelivery.setProviderDeliveryStatus("ACCEPTED");
            assertEquals(1, mapper.insertIssueAlertLog(nestedSourceDelivery));
            PaymentIssueAlertLogEntity acknowledgedSource = buildIssueAlertLog("PIA-OUTBOX-ACKNOWLEDGED");
            acknowledgedSource.setGenerationKey("ISSUE_ACTIVE:PIA-OUTBOX-ACKNOWLEDGED");
            acknowledgedSource.setAckStatus("已确认");
            acknowledgedSource.setAckStatusType("success");
            assertEquals(1, mapper.insertIssueAlertLog(acknowledgedSource));
            PaymentIssueAlertLogEntity acknowledgedSourceDelivery =
                    buildIssueAlertLog("PIA-RECEIPT-ACKNOWLEDGED-SOURCE");
            acknowledgedSourceDelivery.setGenerationKey("ISSUE_DELIVERY:PIA-RECEIPT-ACKNOWLEDGED-SOURCE");
            acknowledgedSourceDelivery.setSourceAlertNo(acknowledgedSource.getAlertNo());
            acknowledgedSourceDelivery.setAlertChannel("SMS");
            acknowledgedSourceDelivery.setAlertStatus("已派发");
            acknowledgedSourceDelivery.setProviderDeliveryStatus("ACCEPTED");
            assertEquals(1, mapper.insertIssueAlertLog(acknowledgedSourceDelivery));
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log "
                        + "SET created_at = TIMESTAMPADD(MINUTE, -2, NOW()) "
                        + "WHERE alert_no IN ('PIA-RECEIPT-RETRY', 'PIA-RECEIPT-ORPHAN', "
                        + "'PIA-RECEIPT-WRONG-ISSUE', 'PIA-RECEIPT-NESTED-SOURCE', "
                        + "'PIA-RECEIPT-ACKNOWLEDGED-SOURCE')");
            }

            assertEquals(1, mapper.findDueAcceptedIssueAlertDeliveryLogs(60, 2, 30, 10).size());
            org.junit.jupiter.api.Assertions.assertTrue(mapper.isMatchingPendingSourceIssueAlert(
                    source.getAlertNo(), accepted.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingPendingSourceIssueAlert(
                    wrongIssueSource.getAlertNo(), wrongIssueDelivery.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingPendingSourceIssueAlert(
                    nestedSource.getAlertNo(), nestedSourceDelivery.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertFalse(mapper.isMatchingPendingSourceIssueAlert(
                    acknowledgedSource.getAlertNo(), acknowledgedSourceDelivery.getIssueNo()));
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(orphan.getAlertNo()));
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(wrongIssueDelivery.getAlertNo()));
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(nestedSourceDelivery.getAlertNo()));
            org.junit.jupiter.api.Assertions.assertNull(mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(
                    acknowledgedSourceDelivery.getAlertNo()));
            assertEquals(0, mapper.claimIssueAlertReceiptQuery(
                    acknowledgedSourceDelivery.getAlertNo(),
                    acknowledgedSourceDelivery.getSourceAlertNo(),
                    acknowledgedSourceDelivery.getIssueNo(),
                    0, true, 2, 30));
            assertEquals(0, mapper.claimIssueAlertReceiptQuery(
                    orphan.getAlertNo(), "PIA-OUTBOX-MISSING", orphan.getIssueNo(), 0, true, 2, 30));
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log "
                        + "SET receipt_query_count = 1, receipt_query_result = 'QUERYING' "
                        + "WHERE alert_no IN ('PIA-RECEIPT-ORPHAN', "
                        + "'PIA-RECEIPT-WRONG-ISSUE', "
                        + "'PIA-RECEIPT-NESTED-SOURCE', 'PIA-RECEIPT-ACKNOWLEDGED-SOURCE')");
            }
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    orphan.getAlertNo(), "PIA-OUTBOX-MISSING", orphan.getIssueNo(),
                    1, "FAILED", 60, 2));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    wrongIssueDelivery.getAlertNo(), wrongIssueDelivery.getSourceAlertNo(),
                    wrongIssueDelivery.getIssueNo(), 1, "FAILED", 60, 2));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    nestedSourceDelivery.getAlertNo(), nestedSourceDelivery.getSourceAlertNo(),
                    nestedSourceDelivery.getIssueNo(), 1, "FAILED", 60, 2));
            assertEquals(1, mapper.completeIssueAlertReceiptQueryAttempt(
                    acknowledgedSourceDelivery.getAlertNo(), acknowledgedSourceDelivery.getSourceAlertNo(),
                    acknowledgedSourceDelivery.getIssueNo(), 1, "FAILED", 60, 2));
            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log "
                        + "SET receipt_query_result = 'QUERYING' "
                        + "WHERE alert_no = 'PIA-RECEIPT-ACKNOWLEDGED-SOURCE'");
            }
            PaymentIssueAlertLogEntity orphanUpdate = new PaymentIssueAlertLogEntity();
            orphanUpdate.setAlertNo(orphan.getAlertNo());
            orphanUpdate.setProviderDeliveryStatus("DELIVERED");
            orphanUpdate.setAckStatus("已确认");
            orphanUpdate.setReceiptQueryCount(1);
            assertEquals(0, mapper.updateIssueAlertProviderReceipt(orphanUpdate));
            PaymentIssueAlertLogEntity nestedSourceUpdate = new PaymentIssueAlertLogEntity();
            nestedSourceUpdate.setAlertNo(nestedSourceDelivery.getAlertNo());
            nestedSourceUpdate.setSourceAlertNo(nestedSourceDelivery.getSourceAlertNo());
            nestedSourceUpdate.setIssueNo(nestedSourceDelivery.getIssueNo());
            nestedSourceUpdate.setProviderDeliveryStatus("DELIVERED");
            nestedSourceUpdate.setAckStatus("已确认");
            nestedSourceUpdate.setReceiptQueryCount(1);
            assertEquals(0, mapper.updateIssueAlertProviderReceipt(nestedSourceUpdate));
            PaymentIssueAlertLogEntity acknowledgedSourceUpdate = new PaymentIssueAlertLogEntity();
            acknowledgedSourceUpdate.setAlertNo(acknowledgedSourceDelivery.getAlertNo());
            acknowledgedSourceUpdate.setSourceAlertNo(acknowledgedSourceDelivery.getSourceAlertNo());
            acknowledgedSourceUpdate.setIssueNo(acknowledgedSourceDelivery.getIssueNo());
            acknowledgedSourceUpdate.setProviderDeliveryStatus("DELIVERED");
            acknowledgedSourceUpdate.setAckStatus("已确认");
            acknowledgedSourceUpdate.setReceiptQueryCount(1);
            assertEquals(1, mapper.updateIssueAlertProviderReceipt(acknowledgedSourceUpdate));
            assertEquals(0, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), wrongIssueSource.getAlertNo(), accepted.getIssueNo(), 0, false, 2, 30));
            assertEquals(1, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 0, false, 2, 30));
            assertEquals(0, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 0, false, 2, 30));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), wrongIssueSource.getAlertNo(), accepted.getIssueNo(),
                    1, "ACCEPTED", 60, 2));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), wrongIssueSource.getIssueNo(),
                    1, "ACCEPTED", 60, 2));
            assertEquals(1, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(),
                    1, "ACCEPTED", 60, 2));
            assertEquals(0, mapper.findDueAcceptedIssueAlertDeliveryLogs(60, 2, 30, 10).size());

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log "
                        + "SET next_receipt_query_at = TIMESTAMPADD(SECOND, -1, NOW()) "
                        + "WHERE alert_no = 'PIA-RECEIPT-RETRY'");
            }
            session.clearCache();
            List<PaymentIssueAlertLogEntity> due = mapper.findDueAcceptedIssueAlertDeliveryLogs(60, 2, 30, 10);
            assertEquals(1, due.size());
            assertEquals(Integer.valueOf(1), due.get(0).getReceiptQueryCount());
            assertEquals(1, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 1, false, 2, 30));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(),
                    1, "FAILED", 120, 2));
            assertEquals(1, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(),
                    2, "FAILED", 120, 2));
            assertEquals(0, mapper.findDueAcceptedIssueAlertDeliveryLogs(60, 2, 30, 10).size());
            List<PaymentIssueAlertLogEntity> manualCandidates = mapper.findAcceptedIssueAlertDeliveryLogs(10);
            assertEquals(1, manualCandidates.size());
            assertEquals(Integer.valueOf(2), manualCandidates.get(0).getReceiptQueryCount());
            PaymentIssueAlertLogEntity specified =
                    mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(accepted.getAlertNo());
            assertEquals(Integer.valueOf(2), specified.getReceiptQueryCount());
            assertEquals(1, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 2, true, 2, 30));

            try (Statement statement = session.getConnection().createStatement()) {
                statement.executeUpdate("UPDATE t_payment_issue_alert_log "
                        + "SET ack_status = '已确认', ack_status_type = 'success' "
                        + "WHERE alert_no = 'PIA-RECEIPT-RETRY'");
            }
            session.clearCache();
            assertEquals(0, mapper.findAcceptedIssueAlertDeliveryLogs(10).size());
            org.junit.jupiter.api.Assertions.assertNull(
                    mapper.findAcceptedIssueAlertDeliveryLogByAlertNo(accepted.getAlertNo()));
            assertEquals(0, mapper.claimIssueAlertReceiptQuery(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(), 3, true, 2, 30));
            assertEquals(0, mapper.completeIssueAlertReceiptQueryAttempt(
                    accepted.getAlertNo(), accepted.getSourceAlertNo(), accepted.getIssueNo(),
                    3, "FAILED", 120, 2));
            PaymentIssueAlertLogEntity lateUpdate = new PaymentIssueAlertLogEntity();
            lateUpdate.setAlertNo(accepted.getAlertNo());
            lateUpdate.setProviderDeliveryStatus("DELIVERED");
            lateUpdate.setAckStatus("已确认");
            lateUpdate.setReceiptQueryCount(3);
            assertEquals(0, mapper.updateIssueAlertProviderReceipt(lateUpdate));
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
            statement.execute("CREATE ALIAS IF NOT EXISTS FIND_IN_SET FOR '"
                    + PaymentTaskCenterMapperIntegrationTest.class.getName() + ".findInSet'");
            statement.execute("CREATE ALIAS IF NOT EXISTS WEEKDAY FOR '"
                    + PaymentTaskCenterMapperIntegrationTest.class.getName() + ".weekday'");
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentTaskCenterMapperIntegrationTest.class.getName() + ".dateFormat'");
            statement.execute("CREATE ALIAS IF NOT EXISTS STR_TO_DATE FOR '"
                    + PaymentTaskCenterMapperIntegrationTest.class.getName() + ".strToDate'");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_query_rate_limit_log");
            statement.execute("DROP TABLE IF EXISTS t_refund_channel_query_rate_limit_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_task_lease");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_duty_roster");
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("DROP TABLE IF EXISTS t_refund_operation_log");
            statement.execute("DROP TABLE IF EXISTS t_refund_attempt");
            statement.execute("DROP TABLE IF EXISTS t_refund_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_expiry_close_retry");
            statement.execute("DROP TABLE IF EXISTS t_prepay_order");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_transaction_no VARCHAR(128), "
                    + "status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_prepay_order ("
                    + "payment_order_id VARCHAR(64), expires_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "attempt_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_expiry_close_retry ("
                    + "payment_order_id VARCHAR(64) NOT NULL UNIQUE, next_retry_at DATETIME NOT NULL)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, notify_no VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64), "
                    + "payment_order_id VARCHAR(64), biz_no VARCHAR(64), "
                    + "event_type VARCHAR(64), publish_status VARCHAR(32), "
                    + "last_published_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "channel_code VARCHAR(64), status VARCHAR(32))");
            statement.execute("CREATE TABLE t_refund_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "channel_code VARCHAR(64), channel_refund_no VARCHAR(128), "
                    + "channel_submitted_at DATETIME, status VARCHAR(32), applied_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_attempt_no VARCHAR(64), "
                    + "refund_order_id VARCHAR(64), channel_code VARCHAR(64), "
                    + "channel_refund_no VARCHAR(128), attempt_status VARCHAR(32), "
                    + "attempt_status_type VARCHAR(32), submitted_at DATETIME, "
                    + "completed_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_refund_operation_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, refund_order_id VARCHAR(64), "
                    + "refund_attempt_no VARCHAR(64), action_code VARCHAR(64), created_at DATETIME)");
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
                    + "receipt_query_count INT NOT NULL DEFAULT 0, last_receipt_query_at DATETIME, "
                    + "next_receipt_query_at DATETIME, receipt_query_result VARCHAR(32), "
                    + "rendered_content_snapshot VARCHAR(1024), triggered_by VARCHAR(64), "
                    + "ack_operator VARCHAR(64), ack_at DATETIME, created_at DATETIME, "
                    + "UNIQUE (alert_no), UNIQUE (generation_key))");
            statement.execute("CREATE TABLE t_payment_issue_duty_roster ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, issue_type VARCHAR(64), severity VARCHAR(32), "
                    + "responsibility_group VARCHAR(64), receiver VARCHAR(64), "
                    + "notify_channels VARCHAR(128), escalation_level VARCHAR(32), "
                    + "escalation_receiver VARCHAR(64), escalation_policy VARCHAR(128), "
                    + "escalation_timeout_minutes INT, schedule_tag VARCHAR(64), "
                    + "effective_start_hour INT, effective_end_hour INT, weekday_scope VARCHAR(32), "
                    + "holiday_strategy VARCHAR(32), status VARCHAR(32))");

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
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, channel_code, channel_transaction_no, status, created_at) VALUES "
                    + "('PAY-EXPIRY-DUE', 'wx_h5', NULL, 'PREPAY_CREATED', NOW()),"
                    + "('PAY-EXPIRY-RETRY-DUE', 'wx_h5', NULL, 'PREPAY_CREATED', NOW()),"
                    + "('PAY-EXPIRY-BACKOFF', 'wx_h5', NULL, 'PREPAY_CREATED', NOW())");
            statement.executeUpdate("INSERT INTO t_prepay_order(payment_order_id, expires_at) VALUES "
                    + "('PAY-EXPIRY-DUE', TIMESTAMPADD(MINUTE, -10, NOW())),"
                    + "('PAY-EXPIRY-RETRY-DUE', TIMESTAMPADD(MINUTE, -9, NOW())),"
                    + "('PAY-EXPIRY-BACKOFF', TIMESTAMPADD(MINUTE, -8, NOW()))");
            statement.executeUpdate("INSERT INTO t_payment_expiry_close_retry("
                    + "payment_order_id, next_retry_at) VALUES "
                    + "('PAY-EXPIRY-RETRY-DUE', TIMESTAMPADD(SECOND, -1, NOW())),"
                    + "('PAY-EXPIRY-BACKOFF', TIMESTAMPADD(MINUTE, 5, NOW()))");
            statement.executeUpdate("INSERT INTO t_payment_event("
                    + "event_no, payment_order_id, biz_no, event_type, publish_status, "
                    + "last_published_at, created_at) "
                    + "VALUES ('EVTR-UNKNOWN-001', 'PAY-NO-HISTORY', 'REF-NOT-IN-TASK', "
                    + "'REFUND_SUBMIT', 'UNKNOWN', "
                    + "TIMESTAMPADD(MINUTE, -31, NOW()), TIMESTAMPADD(MINUTE, -31, NOW()))");
            statement.executeUpdate("INSERT INTO t_payment_issue_duty_roster("
                    + "issue_type, severity, responsibility_group, receiver, escalation_receiver, "
                    + "escalation_policy, escalation_timeout_minutes, schedule_tag, effective_start_hour, "
                    + "effective_end_hour, weekday_scope, holiday_strategy, status) VALUES ("
                    + "'退款提交结果待核查', 'P1', '账务清结算联动组', '账清结算联动值班', "
                    + "'账清结算负责人', '30分钟未核查升级账清结算负责人', 30, '退款核查全时段', "
                    + "0, 23, '1,2,3,4,5,6,7', 'ALL_DAYS', 'ENABLED')");

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
            insertUnknownRefund(connection, "REF-UNKNOWN-NO-HISTORY", "wx_h5",
                    "UNKNOWN", now.minusMinutes(11));
            insertUnknownRefund(connection, "REF-UNKNOWN-BACKOFF-WAIT", "offline_bank",
                    "UNKNOWN", now.minusMinutes(10));
            insertUnknownRefund(connection, "REF-UNKNOWN-BACKOFF-DUE", "offline_bank",
                    "UNKNOWN", now.minusMinutes(9));
            insertUnknownRefund(connection, "REF-UNKNOWN-FRESH", "wx_h5", "UNKNOWN", now);
            insertUnknownRefund(connection, "REF-UNKNOWN-PENDING", "wx_h5",
                    "PENDING", now.minusMinutes(20));
            insertRefundQueryHistory(connection, "REF-BASE-WAIT", now, 30);
            insertRefundQueryHistory(connection, "REF-BACKOFF-WAIT", now, 300, 90);
            insertRefundQueryHistory(connection, "REF-BACKOFF-DUE", now, 300, 130);
            insertRefundQueryHistory(connection, "REF-MAX-WAIT", now, 2100, 1800, 1500, 1200, 800);
            insertRefundQueryHistory(connection, "REF-MAX-DUE", now, 2100, 1800, 1500, 1200, 920);
            insertRefundQueryFailureHistory(connection, "REF-UNKNOWN-BACKOFF-WAIT", now, 30);
            insertRefundQueryFailureHistory(connection, "REF-UNKNOWN-BACKOFF-DUE", now, 90);
            insertRefundQueryMonitorFixtures(connection, now);
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

    public static int findInSet(String value, String values) {
        if (value == null || values == null) {
            return 0;
        }
        String[] candidates = values.split(",");
        for (int index = 0; index < candidates.length; index++) {
            if (value.equals(candidates[index])) {
                return index + 1;
            }
        }
        return 0;
    }

    public static int weekday(java.sql.Date date) {
        return date.toLocalDate().getDayOfWeek().getValue() - 1;
    }

    public static String dateFormat(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        String javaPattern = pattern
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%i", "mm")
                .replace("%s", "ss");
        return timestamp.toLocalDateTime().format(java.time.format.DateTimeFormatter.ofPattern(javaPattern));
    }

    public static Timestamp strToDate(String value, String pattern) {
        if (value == null) {
            return null;
        }
        String javaPattern = pattern
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%i", "mm")
                .replace("%s", "ss");
        LocalDateTime parsed = LocalDateTime.parse(
                value,
                java.time.format.DateTimeFormatter.ofPattern(javaPattern));
        return Timestamp.valueOf(parsed);
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

    private PaymentIssueAlertLogEntity findIssueAlertReceipt(Connection connection, String alertNo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT provider_receipt_snapshot, provider_receipt_no, provider_delivery_status, "
                        + "provider_delivery_message, receipt_query_count, receipt_query_result, "
                        + "ack_status, ack_status_type, ack_operator, triggered_by "
                        + "FROM t_payment_issue_alert_log WHERE alert_no = ?")) {
            statement.setString(1, alertNo);
            try (java.sql.ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                PaymentIssueAlertLogEntity entity = new PaymentIssueAlertLogEntity();
                entity.setProviderReceiptSnapshot(resultSet.getString("provider_receipt_snapshot"));
                entity.setProviderReceiptNo(resultSet.getString("provider_receipt_no"));
                entity.setProviderDeliveryStatus(resultSet.getString("provider_delivery_status"));
                entity.setProviderDeliveryMessage(resultSet.getString("provider_delivery_message"));
                entity.setReceiptQueryCount(resultSet.getInt("receipt_query_count"));
                entity.setReceiptQueryResult(resultSet.getString("receipt_query_result"));
                entity.setAckStatus(resultSet.getString("ack_status"));
                entity.setAckStatusType(resultSet.getString("ack_status_type"));
                entity.setAckOperator(resultSet.getString("ack_operator"));
                entity.setTriggeredBy(resultSet.getString("triggered_by"));
                return entity;
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
                        + "channel_submitted_at, status, applied_at) VALUES (?, ?, ?, ?, ?, ?)");
             PreparedStatement attemptStatement = connection.prepareStatement(
                     "INSERT INTO t_refund_attempt(refund_attempt_no, refund_order_id, channel_code, "
                             + "channel_refund_no, attempt_status, attempt_status_type, submitted_at, "
                             + "completed_at, created_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            statement.setString(1, refundOrderId);
            statement.setString(2, withTrace ? channelCode : null);
            statement.setString(3, withTrace ? "CHANNEL-" + refundOrderId : null);
            statement.setTimestamp(4, withTrace ? Timestamp.valueOf(submittedAt) : null);
            statement.setString(5, status);
            statement.setTimestamp(6, Timestamp.valueOf(submittedAt));
            statement.executeUpdate();
            if (withTrace) {
                attemptStatement.setString(1, "RFA-" + refundOrderId);
                attemptStatement.setString(2, refundOrderId);
                attemptStatement.setString(3, channelCode);
                attemptStatement.setString(4, "CHANNEL-" + refundOrderId);
                attemptStatement.setString(5, "SUCCESS".equals(status) ? "SUCCESS" : "ACCEPTED");
                attemptStatement.setString(6, "SUCCESS".equals(status) ? "success" : "warn");
                attemptStatement.setTimestamp(7, Timestamp.valueOf(submittedAt));
                attemptStatement.setTimestamp(8,
                        "SUCCESS".equals(status) ? Timestamp.valueOf(submittedAt) : null);
                attemptStatement.setTimestamp(9, Timestamp.valueOf(submittedAt.minusHours(1)));
                attemptStatement.executeUpdate();
            }
        }
    }

    private void insertRefundQueryHistory(
            Connection connection,
            String refundOrderId,
            LocalDateTime now,
            int... secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_operation_log(refund_order_id, refund_attempt_no, action_code, created_at) "
                        + "VALUES (?, ?, ?, ?)")) {
            for (int seconds : secondsAgo) {
                statement.setString(1, refundOrderId);
                statement.setString(2, "RFA-" + refundOrderId);
                statement.setString(3, "CHANNEL_QUERY");
                statement.setTimestamp(4, Timestamp.valueOf(now.minusSeconds(seconds)));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertUnknownRefund(
            Connection connection,
            String refundOrderId,
            String channelCode,
            String publishStatus,
            LocalDateTime eventAt) throws Exception {
        try (PreparedStatement refundStatement = connection.prepareStatement(
                "INSERT INTO t_refund_order(refund_order_id, channel_code, channel_refund_no, "
                        + "channel_submitted_at, status, applied_at) VALUES (?, ?, NULL, NULL, 'PROCESSING', ?)");
             PreparedStatement attemptStatement = connection.prepareStatement(
                     "INSERT INTO t_refund_attempt(refund_attempt_no, refund_order_id, channel_code, "
                             + "channel_refund_no, attempt_status, attempt_status_type, submitted_at, "
                             + "completed_at, created_at) VALUES (?, ?, ?, NULL, 'UNKNOWN', 'warn', ?, NULL, ?)");
             PreparedStatement eventStatement = connection.prepareStatement(
                     "INSERT INTO t_payment_event(event_no, payment_order_id, biz_no, event_type, "
                             + "publish_status, last_published_at, created_at) "
                             + "VALUES (?, ?, ?, 'REFUND_SUBMIT', ?, ?, ?)")) {
            refundStatement.setString(1, refundOrderId);
            refundStatement.setString(2, channelCode);
            refundStatement.setTimestamp(3, Timestamp.valueOf(eventAt));
            refundStatement.executeUpdate();
            String refundAttemptNo = "RFA-" + refundOrderId;
            attemptStatement.setString(1, refundAttemptNo);
            attemptStatement.setString(2, refundOrderId);
            attemptStatement.setString(3, channelCode);
            attemptStatement.setTimestamp(4, Timestamp.valueOf(eventAt));
            attemptStatement.setTimestamp(5, Timestamp.valueOf(eventAt));
            attemptStatement.executeUpdate();
            eventStatement.setString(1, "EVTR-" + refundOrderId);
            eventStatement.setString(2, "PAY-" + refundOrderId);
            eventStatement.setString(3, refundAttemptNo);
            eventStatement.setString(4, publishStatus);
            eventStatement.setTimestamp(5, Timestamp.valueOf(eventAt));
            eventStatement.setTimestamp(6, Timestamp.valueOf(eventAt));
            eventStatement.executeUpdate();
        }
    }

    private void insertRefundQueryFailureHistory(
            Connection connection,
            String refundOrderId,
            LocalDateTime now,
            int secondsAgo) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_operation_log(refund_order_id, refund_attempt_no, action_code, created_at) "
                        + "VALUES (?, ?, 'CHANNEL_QUERY_FAILED', ?)")) {
            statement.setString(1, refundOrderId);
            statement.setString(2, "RFA-" + refundOrderId);
            statement.setTimestamp(3, Timestamp.valueOf(now.minusSeconds(secondsAgo)));
            statement.executeUpdate();
        }
    }

    private void insertRefundQueryMonitorFixtures(Connection connection, LocalDateTime now) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO t_refund_operation_log(refund_order_id, action_code, created_at) VALUES (?, ?, ?)")) {
            String[] actionCodes = {
                    "CHANNEL_QUERY",
                    "CHANNEL_QUERY_SUCCESS",
                    "CHANNEL_QUERY_FAIL",
                    "CHANNEL_QUERY_FAILED",
                    "RETRY",
                    "CHANNEL_QUERY_FAILED"
            };
            int[] secondsAgo = {60, 120, 180, 240, 300, 90000};
            for (int index = 0; index < actionCodes.length; index++) {
                statement.setString(1, "REF-MONITOR");
                statement.setString(2, actionCodes[index]);
                statement.setTimestamp(3, Timestamp.valueOf(now.minusSeconds(secondsAgo[index])));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }
}
