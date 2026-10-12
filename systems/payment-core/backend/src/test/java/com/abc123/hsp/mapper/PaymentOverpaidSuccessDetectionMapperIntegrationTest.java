package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentIssueResponsibilitySummaryDTO;
import com.abc123.hsp.dto.PaymentIssueRowDTO;
import java.io.InputStream;
import java.math.BigDecimal;
import java.sql.Statement;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
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
 * 同订单成功收款超过订单应收后的异常与日终差异集成测试。
 */
public class PaymentOverpaidSuccessDetectionMapperIntegrationTest {

    private SqlSessionFactory sqlSessionFactory;

    @BeforeEach
    void setUp() throws Exception {
        UnpooledDataSource dataSource = new UnpooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:payment_overpaid_success;MODE=MySQL;"
                        + "DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        configuration.setLogImpl(org.apache.ibatis.logging.nologging.NoLoggingImpl.class);
        configuration.setMapUnderscoreToCamelCase(true);
        configuration.addMapper(PaymentIssueMapper.class);
        configuration.addMapper(PaymentDayEndMapper.class);
        parseMapper(configuration, "mapper/PaymentIssueMapper.xml");
        parseMapper(configuration, "mapper/RefundMapper.xml");
        parseMapper(configuration, "mapper/PaymentDayEndMapper.xml");
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
        createTablesAndFixtures();
    }

    @Test
    void shouldExposeOnlyOrderWhoseSuccessfulPaymentsExceedReceivable() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueRowDTO issue = mapper.findByIssueNo("ISSUE-OVERPAID-ORD-OVERPAID");

            assertEquals("PAY-OVERPAID-2", issue.getPaymentOrderId());
            assertEquals("订单成功收款超额", issue.getIssueType());
            assertEquals("P1", issue.getSeverity());
            assertTrue(issue.getIssueSummary().contains("订单应收=100.00"));
            assertTrue(issue.getIssueSummary().contains("超额=10.00"));

            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            assertEquals(1L, mapper.count(query));
            query.setOrderNo("ORD-SPLIT");
            assertEquals(0L, mapper.count(query));
        }
    }

    @Test
    void shouldKeepUnacceptedSubmissionVisibleWithoutSuggestingPrematureQuery() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, customer_name, "
                    + "amount, payment_method, channel_code, status, status_type, created_at) VALUES "
                    + "('PAY-SUBMIT-UNCONFIRMED', 'ORD-UNCONFIRMED', '客户', 10.00, "
                    + "'微信', 'wx_h5', 'WAIT_CALLBACK', 'warn', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) "
                    + "VALUES ('PAY-SUBMIT-UNCONFIRMED', '渠道提交中', NOW())");
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);

            PaymentIssueRowDTO unaccepted = mapper.findByIssueNo("ISSUE-WAIT-PAY-SUBMIT-UNCONFIRMED");
            assertNotNull(unaccepted);
            assertTrue(unaccepted.getIssueSummary().contains("渠道受理未确认"));
            assertTrue(unaccepted.getRootCauseHint().contains("勿主动查单"));
            assertTrue(unaccepted.getRecommendedAction().contains("核对渠道受理"));
            assertTrue(unaccepted.getResponsibilityHint().contains("勿主动查单"));
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("待回调未收口");
            assertEquals(1L, mapper.count(query));
            assertTrue(mapper.findAll(query).stream().anyMatch(issue ->
                    "ISSUE-WAIT-PAY-SUBMIT-UNCONFIRMED".equals(issue.getIssueNo())
                            && issue.getRootCauseHint().contains("勿主动查单")
                            && issue.getResponsibilityHint().contains("勿主动查单")));
            assertTrue(mapper.responsibilitySummary(query).stream().anyMatch(summary ->
                    summary.getSuggestedAction().contains("勿主动查单")));

            statement.executeUpdate("UPDATE t_payment_order SET channel_transaction_no = 'WX-ACCEPTED' "
                    + "WHERE payment_order_id = 'PAY-SUBMIT-UNCONFIRMED'");
            session.clearCache();

            PaymentIssueRowDTO accepted = mapper.findByIssueNo("ISSUE-WAIT-PAY-SUBMIT-UNCONFIRMED");
            assertTrue(accepted.getRootCauseHint().contains("优先主动查单"));
            assertTrue(accepted.getRecommendedAction().contains("核对回调"));
        }
    }

    @Test
    void shouldGuideChannelTransactionConflictToManualReview() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, customer_name, "
                    + "amount, payment_method, channel_code, status, status_type, created_at) VALUES "
                    + "('PAY-CHANNEL-CONFLICT', 'ORD-CONFLICT', '客户', 10.00, "
                    + "'微信', 'wx_h5', 'WAIT_CALLBACK', 'danger', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) "
                    + "VALUES ('PAY-CHANNEL-CONFLICT', '渠道流水归属冲突，待人工核查', NOW())");
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);

            PaymentIssueRowDTO issue = mapper.findByIssueNo("ISSUE-WAIT-PAY-CHANNEL-CONFLICT");
            assertNotNull(issue);
            assertTrue(issue.getIssueSummary().contains("流水归属冲突"));
            assertTrue(issue.getRootCauseHint().contains("勿主动查单"));
            assertTrue(issue.getRecommendedAction().contains("人工核查"));
            assertTrue(issue.getResponsibilityHint().contains("流水归属"));
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setPaymentOrderId("PAY-CHANNEL-CONFLICT");
            assertTrue(mapper.responsibilitySummary(query).stream().anyMatch(summary ->
                    summary.getSuggestedAction().contains("流水归属")));
        }
    }

    @Test
    void shouldRouteOverpaidSuccessToClearingAndBlockDayEndReadiness() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            List<PaymentIssueResponsibilitySummaryDTO> summaries = issueMapper.responsibilitySummary(query);

            assertEquals(1, summaries.size());
            assertEquals("账务清结算联动组", summaries.get(0).getGroupName());
            assertEquals("danger", summaries.get(0).getGroupType());
            assertEquals(1, summaries.get(0).getP1Count());

            PaymentDayEndMapper dayEndMapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(1, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("30.00")));
        }
    }

    @Test
    void shouldNotCreditAliasedOrUnboundSuccessEvidenceToDayEnd() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(5, mapper.countChannelSuccessByDate(today));
            assertEquals(5, mapper.countInternalSuccessByDate(today));
            assertEquals(1, mapper.countPaymentSuccessGapByDate(today));

            statement.executeUpdate("UPDATE t_payment_notify_log SET payment_order_id='pay-split-1' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            session.clearCache();
            assertEquals(4, mapper.countChannelSuccessByDate(today));
            assertEquals(0, mapper.sumChannelSuccessAmountByDate(today)
                    .compareTo(new BigDecimal("200.00")));
            assertEquals(1, mapper.countChannelAbnormalByDate(today));
            assertEquals(2, mapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, mapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("90.00")));
            statement.executeUpdate("UPDATE t_payment_notify_log SET payment_order_id='PAY-SPLIT-1 ' "
                    + "WHERE payment_order_id='pay-split-1'");
            session.clearCache();
            assertEquals(4, mapper.countChannelSuccessByDate(today));
            statement.executeUpdate("UPDATE t_payment_notify_log SET payment_order_id='PAY-SPLIT-1', "
                    + "channel_code='other_channel' WHERE payment_order_id='PAY-SPLIT-1 '");
            session.clearCache();
            assertEquals(4, mapper.countChannelSuccessByDate(today));
            assertEquals(1, mapper.countChannelAbnormalByDate(today));
            statement.executeUpdate("UPDATE t_payment_notify_log SET channel_code='wx_h5' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            session.clearCache();
            assertEquals(5, mapper.countChannelSuccessByDate(today));

            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='pay-split-1' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            session.clearCache();
            assertEquals(4, mapper.countInternalSuccessByDate(today));
            assertEquals(0, mapper.sumInternalSuccessAmountByDate(today)
                    .compareTo(new BigDecimal("200.00")));
            assertEquals(1, mapper.countInternalAbnormalByDate(today));
            assertEquals(2, mapper.countPaymentSuccessGapByDate(today));
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='PAY-SPLIT-1 ' "
                    + "WHERE payment_order_id='pay-split-1'");
            session.clearCache();
            assertEquals(4, mapper.countInternalSuccessByDate(today));
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='PAY-SPLIT-1', "
                    + "biz_no='ORD-OTHER' WHERE payment_order_id='PAY-SPLIT-1 '");
            session.clearCache();
            assertEquals(4, mapper.countInternalSuccessByDate(today));
            assertEquals(1, mapper.countInternalAbnormalByDate(today));
            statement.executeUpdate("UPDATE t_payment_event SET biz_no='ORD-SPLIT ' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            session.clearCache();
            assertEquals(4, mapper.countInternalSuccessByDate(today));
            statement.executeUpdate("UPDATE t_payment_event SET biz_no='ORD-SPLIT' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            session.clearCache();
            assertEquals(5, mapper.countInternalSuccessByDate(today));
            assertEquals(1, mapper.countPaymentSuccessGapByDate(today));
        }
    }

    @Test
    void shouldNotAttributeAliasedCallbackOrFailedEventToPaymentIssue() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueQueryDTO callbackQuery = new PaymentIssueQueryDTO();
            callbackQuery.setIssueType("回调处理待跟进");
            PaymentIssueQueryDTO eventQuery = new PaymentIssueQueryDTO();
            eventQuery.setIssueType("下游事件发布失败");
            statement.executeUpdate("INSERT INTO t_payment_notify_log(notify_no, payment_order_id, "
                    + "channel_code, notify_type, notify_status, created_at) VALUES "
                    + "('NTF-ALIAS-PENDING', 'pay-split-1', 'wx_h5', 'SUCCESS', '待处理', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_event(event_no, payment_order_id, biz_no, "
                    + "event_type, event_topic, publish_status, retry_count, created_at) VALUES "
                    + "('EVT-ALIAS-FAILED', 'pay-split-1', 'ORD-SPLIT', 'PAYMENT_SUCCESS', "
                    + "'payment.success.v1', 'FAILED', 1, NOW())");
            assertEquals(0L, mapper.count(callbackQuery));
            assertEquals(0L, mapper.count(eventQuery));
            assertNull(mapper.findByIssueNo("ISSUE-NOTIFY-NTF-ALIAS-PENDING"));
            assertNull(mapper.findByIssueNo("ISSUE-EVENT-EVT-ALIAS-FAILED"));

            statement.executeUpdate("UPDATE t_payment_notify_log SET payment_order_id='PAY-SPLIT-1' "
                    + "WHERE notify_no='NTF-ALIAS-PENDING'");
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='PAY-SPLIT-1' "
                    + "WHERE event_no='EVT-ALIAS-FAILED'");
            session.clearCache();
            assertEquals(1L, mapper.count(callbackQuery));
            assertEquals(1L, mapper.count(eventQuery));
            assertNotNull(mapper.findByIssueNo("ISSUE-NOTIFY-NTF-ALIAS-PENDING"));
            assertNotNull(mapper.findByIssueNo("ISSUE-EVENT-EVT-ALIAS-FAILED"));
        }
    }

    @Test
    void shouldNotBorrowAliasedAttemptOrCallbackForWaitingIssue() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET status='WAIT_CALLBACK' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            statement.executeUpdate("INSERT INTO t_payment_attempt(payment_order_id, attempt_status, created_at) "
                    + "VALUES ('pay-split-1', '失败', DATEADD('SECOND', 1, NOW()))");
            statement.executeUpdate("INSERT INTO t_payment_notify_log(notify_no, payment_order_id, "
                    + "channel_code, notify_type, notify_status, created_at) VALUES "
                    + "('NTF-ALIAS-WAIT', 'pay-split-1', 'wx_h5', 'SUCCESS', '待处理', "
                    + "DATEADD('SECOND', 1, NOW()))");
            PaymentIssueRowDTO issue = session.getMapper(PaymentIssueMapper.class)
                    .findByIssueNo("ISSUE-WAIT-PAY-SPLIT-1");
            assertNotNull(issue);
            assertTrue(issue.getRootCauseHint().contains("无支付尝试"));
            assertTrue(issue.getIssueSummary().contains("已收口"));
        }
    }

    @Test
    void shouldNotBorrowAliasedActionsAndAlertsForCanonicalIssue() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            String issueNo = "ISSUE-OVERPAID-ORD-OVERPAID";
            String alias = "issue-overpaid-ord-overpaid";
            assertNull(mapper.findByIssueNo(alias));
            statement.executeUpdate("INSERT INTO t_payment_issue_action_log(issue_no, action_type, "
                    + "action_remark, created_at) VALUES ('" + alias + "', '认领', '其他异常处理', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_issue_alert_log(issue_no, alert_channel, "
                    + "alert_status, receiver, created_at) VALUES "
                    + "('" + alias + "', 'IN_APP_OUTBOX', '已派发', 'other-owner', NOW()), "
                    + "('" + alias + "', 'SMS', '已派发', 'other-owner', NOW())");
            session.clearCache();
            PaymentIssueRowDTO detail = mapper.findByIssueNo(issueNo);
            assertEquals("暂无处理动作", detail.getLatestActionSummary());
            assertEquals("未派发", detail.getAlertChannelSummary());
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            PaymentIssueRowDTO listed = mapper.findAll(query).get(0);
            assertEquals("暂无处理动作", listed.getLatestActionSummary());
            assertEquals("未派发", listed.getAlertChannelSummary());

            statement.executeUpdate("UPDATE t_payment_issue_action_log SET issue_no='" + issueNo + "'");
            statement.executeUpdate("UPDATE t_payment_issue_alert_log SET issue_no='" + issueNo + "'");
            session.clearCache();
            assertTrue(mapper.findByIssueNo(issueNo).getLatestActionSummary().contains("其他异常处理"));
            assertEquals("SMS", mapper.findByIssueNo(issueNo).getAlertChannelSummary());
        }
    }

    @Test
    void shouldAcknowledgeOnlyExactIssueAndAlertNumbers() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            statement.executeUpdate("INSERT INTO t_payment_issue_alert_log(alert_no, issue_no, "
                    + "alert_channel, ack_status, created_at) VALUES "
                    + "('PIA-ACK-001', 'issue-overpaid-ord-overpaid', 'SMS', '待确认', NOW())");
            assertEquals(0, mapper.acknowledgePendingAlerts("ISSUE-OVERPAID-ORD-OVERPAID", "ops"));
            assertEquals(0, mapper.acknowledgeAlertByAlertNo("pia-ack-001", "ops"));
            assertEquals(1, mapper.acknowledgeAlertByAlertNo("PIA-ACK-001", "ops"));

            statement.executeUpdate("INSERT INTO t_payment_issue_alert_log(alert_no, issue_no, "
                    + "alert_channel, ack_status, created_at) VALUES "
                    + "('PIA-ACK-002', 'ISSUE-OVERPAID-ORD-OVERPAID', 'SMS', '待确认', NOW())");
            assertEquals(1, mapper.acknowledgePendingAlerts("ISSUE-OVERPAID-ORD-OVERPAID", "ops"));
        }
    }

    @Test
    void shouldNotAttributeAliasedRefundSubmitEventOrDisabledChannel() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueQueryDTO refundQuery = new PaymentIssueQueryDTO();
            refundQuery.setIssueType("退款提交结果待核查");
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='pay-refund-unknown' "
                    + "WHERE event_no='EVTR-UNKNOWN-001'");
            session.clearCache();
            assertEquals(0L, mapper.count(refundQuery));
            statement.executeUpdate("UPDATE t_payment_event SET payment_order_id='PAY-REFUND-UNKNOWN' "
                    + "WHERE event_no='EVTR-UNKNOWN-001'");
            session.clearCache();
            assertEquals(1L, mapper.count(refundQuery));

            PaymentIssueQueryDTO channelQuery = new PaymentIssueQueryDTO();
            channelQuery.setIssueType("命中停用渠道");
            statement.executeUpdate("UPDATE t_payment_channel_config "
                    + "SET channel_code='WX_H5', status='DISABLED'");
            session.clearCache();
            assertEquals(0L, mapper.count(channelQuery));
            statement.executeUpdate("UPDATE t_payment_channel_config SET channel_code='wx_h5'");
            session.clearCache();
            assertEquals(5L, mapper.count(channelQuery));
        }
    }

    @Test
    void shouldNotLetHistoricalNegativeSuccessMaskPositiveOverpayment() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_order(order_no, order_amount) "
                    + "VALUES ('ORD-NEGATIVE-HISTORY', 100.00)");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "status, status_type, success_at, created_at) VALUES "
                    + "('PAY-NEGATIVE-FIRST', 'ORD-NEGATIVE-HISTORY', '客户', 80.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-NEGATIVE-LEGACY', 'ORD-NEGATIVE-HISTORY', '客户', -20.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW(), NOW()), "
                    + "('PAY-NEGATIVE-LAST', 'ORD-NEGATIVE-HISTORY', '客户', 30.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_payment_notify_log("
                    + "notify_no, payment_order_id, channel_code, notify_type, notify_status, created_at) "
                    + "SELECT CONCAT('NTF-', payment_order_id), payment_order_id, channel_code, "
                    + "'SUCCESS', '已收口', NOW() FROM t_payment_order WHERE order_no = 'ORD-NEGATIVE-HISTORY'");
            statement.executeUpdate("INSERT INTO t_payment_event("
                    + "event_no, payment_order_id, biz_no, event_type, event_topic, publish_status, retry_count, created_at) "
                    + "SELECT CONCAT('EVT-', payment_order_id), payment_order_id, order_no, 'PAYMENT_SUCCESS', "
                    + "'payment.success.v1', 'SUCCESS', 0, NOW() FROM t_payment_order "
                    + "WHERE order_no = 'ORD-NEGATIVE-HISTORY'");

            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueRowDTO issue = issueMapper.findByIssueNo("ISSUE-OVERPAID-ORD-NEGATIVE-HISTORY");
            assertEquals("PAY-NEGATIVE-LAST", issue.getPaymentOrderId());
            assertTrue(issue.getIssueSummary().contains("订单成功支付合计=110.00"));
            assertTrue(issue.getIssueSummary().contains("超额=10.00"));
            assertTrue(issue.getIssueSummary().contains("成功支付单数=2"));
            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            assertEquals(2L, issueMapper.count(query));

            PaymentDayEndMapper dayEndMapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(2, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("60.00")));

            statement.executeUpdate("DELETE FROM t_payment_event "
                    + "WHERE payment_order_id = 'PAY-NEGATIVE-LEGACY'");
            session.clearCache();
            assertEquals(3, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("60.00")));
        }
    }

    @Test
    void shouldNotMergeCaseVariantOrderNumbersIntoOverpaidSuccess() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("UPDATE t_payment_order SET order_no='ord-split' "
                    + "WHERE payment_order_id='PAY-SPLIT-1'");
            statement.executeUpdate("UPDATE t_payment_order SET amount=50.00 "
                    + "WHERE payment_order_id='PAY-SPLIT-2'");
            statement.executeUpdate("INSERT INTO t_order(order_no, order_amount) VALUES ('ord-split', 100.00)");

            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            assertEquals(1L, issueMapper.count(query));
            assertNull(issueMapper.findByIssueNo("ISSUE-OVERPAID-ORD-SPLIT"));
            assertNotNull(issueMapper.findByIssueNo("ISSUE-OVERPAID-ORD-OVERPAID"));
            PaymentDayEndMapper dayEndMapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(2, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("90.00")));
        }
    }

    @Test
    void shouldNotAttributePaymentToOnlyCaseVariantSourceOrder() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_order(order_no, order_amount) VALUES ('ord-alias', 20.00)");
            statement.executeUpdate("INSERT INTO t_payment_order(payment_order_id, order_no, customer_name, "
                    + "amount, payment_method, channel_code, status, status_type, success_at, created_at) "
                    + "VALUES ('PAY-ALIAS', 'ORD-ALIAS', '客户', 30.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW(), NOW())");
            statement.executeUpdate("INSERT INTO t_payment_notify_log(notify_no, payment_order_id, "
                    + "channel_code, notify_type, notify_status, created_at) "
                    + "VALUES ('NTF-ALIAS', 'PAY-ALIAS', 'wx_h5', 'SUCCESS', '已收口', NOW())");
            statement.executeUpdate("INSERT INTO t_payment_event(event_no, payment_order_id, biz_no, event_type, "
                    + "event_topic, publish_status, retry_count, created_at) "
                    + "VALUES ('EVT-ALIAS', 'PAY-ALIAS', 'ORD-ALIAS', 'PAYMENT_SUCCESS', 'payment.success.v1', "
                    + "'SUCCESS', 0, NOW())");

            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("订单成功收款超额");
            PaymentIssueMapper issueMapper = session.getMapper(PaymentIssueMapper.class);
            assertEquals(1L, issueMapper.count(query));
            assertNull(issueMapper.findByIssueNo("ISSUE-OVERPAID-ORD-ALIAS"));
            assertNotNull(issueMapper.findByIssueNo("ISSUE-OVERPAID-ORD-OVERPAID"));
            PaymentDayEndMapper dayEndMapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();
            assertEquals(1, dayEndMapper.countPaymentSuccessGapByDate(today));
            assertEquals(0, dayEndMapper.sumPaymentSuccessGapAmountByDate(today)
                    .compareTo(new BigDecimal("30.00")));
        }
    }

    @Test
    void shouldCountOnlyExplicitPendingPaymentStatesForDayEnd() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, status, status_type, created_at) VALUES "
                    + "('PAY-PENDING-PREPAY', 'ORD-PENDING', '客户一', 10.00, 'PREPAY_CREATED', 'info', NOW()), "
                    + "('PAY-PENDING-CALLBACK', 'ORD-PENDING', '客户二', 20.00, 'WAIT_CALLBACK', 'warn', NOW()), "
                    + "('PAY-PENDING-REVIEW', 'ORD-PENDING', '客户三', 30.00, 'RISK_REVIEW', 'warn', NOW()), "
                    + "('PAY-FINAL-BLOCKED', 'ORD-PENDING', '客户四', 40.00, 'RISK_BLOCKED', 'danger', NOW()), "
                    + "('PAY-FINAL-REJECTED', 'ORD-PENDING', '客户五', 50.00, 'RISK_REJECTED', 'danger', NOW())");
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);
            String today = LocalDate.now().toString();

            assertEquals(3, mapper.countPendingPaymentsByDate(today));
            assertEquals(0, mapper.sumPendingPaymentAmountByDate(today)
                    .compareTo(new BigDecimal("60.00")));
        }
    }

    @Test
    void shouldAssignSuccessfulPaymentsBySuccessTimeAndExposeMissingTime() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            String successDate = LocalDate.now().minusDays(30).toString();
            String creationDate = LocalDate.now().minusDays(31).toString();
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, status, status_type, "
                    + "success_at, created_at) VALUES "
                    + "('PAY-CROSS-DAY', 'ORD-CROSS-DAY', '跨日客户', 66.00, 'SUCCESS', 'success', '"
                    + successDate + " 00:05:00', '" + creationDate + " 23:55:00'), "
                    + "('PAY-MISSING-SUCCESS-TIME', 'ORD-MISSING-TIME', '缺时客户', 77.00, "
                    + "'SUCCESS', 'success', NULL, '" + creationDate + " 12:00:00')");
            PaymentDayEndMapper mapper = session.getMapper(PaymentDayEndMapper.class);

            assertEquals(1, mapper.countSuccessPaymentsByDate(successDate));
            assertEquals(0, mapper.sumSuccessPaymentAmountByDate(successDate)
                    .compareTo(new BigDecimal("66.00")));
            assertEquals(1, mapper.countMissingPaymentSuccessTimeByDate(creationDate));
        }
    }

    @Test
    void shouldExposeUnknownRefundSubmitAsP1ClearingIssue() {
        try (SqlSession session = sqlSessionFactory.openSession()) {
            PaymentIssueMapper mapper = session.getMapper(PaymentIssueMapper.class);
            PaymentIssueRowDTO issue = mapper.findByIssueNo(
                    "ISSUE-REFUND-SUBMIT-UNKNOWN-EVTR-UNKNOWN-001");

            assertEquals("退款提交结果待核查", issue.getIssueType());
            assertEquals("P1", issue.getSeverity());
            assertTrue(issue.getIssueSummary().contains("REF-UNKNOWN-001"));
            assertTrue(issue.getRootCauseHint().contains("禁止直接重发"));
            assertTrue(issue.getRecommendedRoute().contains("publishStatus=UNKNOWN"));

            PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
            query.setIssueType("退款提交结果待核查");
            List<PaymentIssueResponsibilitySummaryDTO> summaries = mapper.responsibilitySummary(query);
            assertEquals(1, summaries.size());
            assertEquals("账务清结算联动组", summaries.get(0).getGroupName());
            assertEquals("danger", summaries.get(0).getGroupType());
            assertEquals(1, summaries.get(0).getP1Count());
        }
    }

    private void parseMapper(Configuration configuration, String resource) throws Exception {
        try (InputStream mapperXml = Resources.getResourceAsStream(resource)) {
            new XMLMapperBuilder(
                    mapperXml,
                    configuration,
                    resource,
                    configuration.getSqlFragments()).parse();
        }
    }

    private void createTablesAndFixtures() throws Exception {
        try (SqlSession session = sqlSessionFactory.openSession();
             Statement statement = session.getConnection().createStatement()) {
            statement.execute("CREATE ALIAS IF NOT EXISTS DATE_FORMAT FOR '"
                    + PaymentOverpaidSuccessDetectionMapperIntegrationTest.class.getName()
                    + ".formatDate'");
            statement.execute("CREATE ALIAS IF NOT EXISTS STR_TO_DATE FOR '"
                    + PaymentOverpaidSuccessDetectionMapperIntegrationTest.class.getName()
                    + ".parseDate'");
            statement.execute("DROP TABLE IF EXISTS t_payment_channel_config");
            statement.execute("DROP TABLE IF EXISTS t_payment_event");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_alert_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_issue_action_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_notify_log");
            statement.execute("DROP TABLE IF EXISTS t_payment_attempt");
            statement.execute("DROP TABLE IF EXISTS t_payment_order");
            statement.execute("DROP TABLE IF EXISTS t_order");
            statement.execute("CREATE TABLE t_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, order_no VARCHAR_IGNORECASE(64), "
                    + "order_amount DECIMAL(18,2))");
            statement.execute("CREATE TABLE t_payment_order ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR(64), "
                    + "order_no VARCHAR_IGNORECASE(64), customer_name VARCHAR(128), amount DECIMAL(18,2), "
                    + "payment_method VARCHAR(32), channel_code VARCHAR(64), "
                    + "channel_transaction_no VARCHAR(128), status VARCHAR(32), "
                    + "status_type VARCHAR(32), success_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_attempt ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, payment_order_id VARCHAR_IGNORECASE(64), "
                    + "attempt_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_notify_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, notify_no VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), channel_code VARCHAR(64), notify_type VARCHAR(32), "
                    + "notify_status VARCHAR(32), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_event ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, event_no VARCHAR(64), "
                    + "payment_order_id VARCHAR_IGNORECASE(64), biz_no VARCHAR(64), event_type VARCHAR(64), "
                    + "event_topic VARCHAR(128), publish_status VARCHAR(32), retry_count INT, "
                    + "last_published_at DATETIME, created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_channel_config ("
                    + "channel_code VARCHAR_IGNORECASE(64), status VARCHAR(32))");
            statement.execute("CREATE TABLE t_payment_issue_action_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, action_no VARCHAR(64), "
                    + "issue_no VARCHAR_IGNORECASE(128), payment_order_id VARCHAR(64), issue_type VARCHAR(64), "
                    + "action_type VARCHAR(32), assignee VARCHAR(64), handling_status VARCHAR(32), "
                    + "handling_status_type VARCHAR(32), action_remark VARCHAR(255), "
                    + "operator VARCHAR(64), created_at DATETIME)");
            statement.execute("CREATE TABLE t_payment_issue_alert_log ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, alert_no VARCHAR_IGNORECASE(64), "
                    + "issue_no VARCHAR_IGNORECASE(128), payment_order_id VARCHAR(64), issue_type VARCHAR(64), "
                    + "severity VARCHAR(32), responsibility_group VARCHAR(64), "
                    + "alert_channel VARCHAR(32), receiver VARCHAR(64), alert_status VARCHAR(32), "
                    + "alert_status_type VARCHAR(32), ack_status VARCHAR(32), ack_status_type VARCHAR(32), "
                    + "provider_code VARCHAR(64), provider_name VARCHAR(128), template_code VARCHAR(128), "
                    + "provider_receipt_no VARCHAR(128), provider_delivery_status VARCHAR(64), "
                    + "provider_delivery_message VARCHAR(255), ack_operator VARCHAR(64), "
                    + "ack_at DATETIME, generation_key VARCHAR(128), created_at DATETIME)");
            statement.executeUpdate("INSERT INTO t_order(order_no, order_amount) VALUES "
                    + "('ORD-SPLIT', 100.00), ('ORD-OVERPAID', 100.00)");
            statement.executeUpdate("INSERT INTO t_payment_channel_config(channel_code, status) "
                    + "VALUES ('wx_h5', 'ENABLED')");
            statement.executeUpdate("INSERT INTO t_payment_order("
                    + "payment_order_id, order_no, customer_name, amount, payment_method, channel_code, "
                    + "status, status_type, created_at) VALUES "
                    + "('PAY-SPLIT-1', 'ORD-SPLIT', '张女士', 60.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-SPLIT-2', 'ORD-SPLIT', '张女士', 40.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-OVERPAID-1', 'ORD-OVERPAID', '李女士', 80.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-OVERPAID-2', 'ORD-OVERPAID', '李女士', 30.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW()), "
                    + "('PAY-REFUND-UNKNOWN', 'ORD-REFUND', '王女士', 50.00, '微信', 'wx_h5', "
                    + "'SUCCESS', 'success', NOW())");
            statement.executeUpdate("UPDATE t_payment_order SET success_at = created_at "
                    + "WHERE status = 'SUCCESS'");
            statement.executeUpdate("INSERT INTO t_payment_notify_log("
                    + "notify_no, payment_order_id, channel_code, notify_type, notify_status, created_at) "
                    + "SELECT CONCAT('NTF-', payment_order_id), payment_order_id, channel_code, "
                    + "'SUCCESS', '已收口', NOW() FROM t_payment_order");
            statement.executeUpdate("INSERT INTO t_payment_event("
                    + "event_no, payment_order_id, biz_no, event_type, event_topic, publish_status, retry_count, created_at) "
                    + "SELECT CONCAT('EVT-', payment_order_id), payment_order_id, order_no, 'PAYMENT_SUCCESS', "
                    + "'payment.success.v1', 'SUCCESS', 0, NOW() FROM t_payment_order");
            statement.executeUpdate("INSERT INTO t_payment_event("
                    + "event_no, payment_order_id, biz_no, event_type, event_topic, publish_status, "
                    + "retry_count, last_published_at, created_at) VALUES "
                    + "('EVTR-UNKNOWN-001', 'PAY-REFUND-UNKNOWN', 'REF-UNKNOWN-001', "
                    + "'REFUND_SUBMIT', 'payment.refund.submitted.v1', 'UNKNOWN', 1, NOW(), NOW())");
            session.commit();
        }
    }

    public static String formatDate(Timestamp timestamp, String pattern) {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(timestamp);
    }

    public static Timestamp parseDate(String value, String pattern) throws java.text.ParseException {
        return new Timestamp(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT)
                .parse(value).getTime());
    }
}
