package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.dto.PaymentIssueAlertLogQueryDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentIssueLiteralSearchIntegrationTest {

    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final Logger transactionLogger = (Logger) LoggerFactory.getLogger(TransactionTemplate.class);
    private final Level originalRootLevel = rootLogger.getLevel();
    private final Level originalTransactionLevel = transactionLogger.getLevel();

    @AfterEach
    void restoreLoggingLevels() {
        rootLogger.setLevel(originalRootLevel);
        transactionLogger.setLevel(originalTransactionLevel);
    }

    @Test
    void shouldFilterIssuesAndAlertLogsByLiteralIdentifiers() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=jdbc:h2:mem:issue_literal_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            PaymentIssueMapper mapper = context.getBean(PaymentIssueMapper.class);
            String suffix = "%_!\\\"";
            String paymentOrderId = "PAY-ISSUE-" + suffix;
            String orderNo = "ORD-ISSUE-" + suffix;
            String channelCode = "channel-" + suffix;
            String alertNo = "ALERT-ISSUE-" + suffix;
            String issueNo = "ISSUE-WAIT-" + paymentOrderId;

            jdbc.update("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, "
                            + "payment_method, channel_code, status, status_type, created_at) "
                            + "VALUES (?, ?, '本地用户', 10.00, '本地隔离方式', ?, 'WAIT_CALLBACK', 'warn', NOW())",
                    paymentOrderId, orderNo, channelCode);
            jdbc.update("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, "
                    + "payment_method, channel_code, status, status_type, created_at) "
                    + "VALUES ('PAY-ISSUE-PLAIN', 'ORD-ISSUE-PLAIN', '本地用户', 10.00, "
                    + "'本地隔离方式', 'channel-plain', 'WAIT_CALLBACK', 'warn', NOW())");
            jdbc.update("INSERT INTO t_payment_issue_alert_log (alert_no, issue_no, payment_order_id, "
                            + "issue_type, severity, responsibility_group, alert_channel, receiver, "
                            + "alert_status, alert_status_type, ack_status, ack_status_type, "
                            + "alert_content, triggered_by, created_at) "
                            + "VALUES (?, ?, ?, '待回调未收口', 'P1', '支付运营', 'IN_APP_OUTBOX', '本地值班', "
                            + "'本地回归', 'success', '待确认', 'warn', '本地回归', 'TEST', NOW())",
                    alertNo, issueNo, paymentOrderId);
            jdbc.update("INSERT INTO t_payment_issue_alert_log (alert_no, issue_no, payment_order_id, "
                    + "issue_type, severity, responsibility_group, alert_channel, receiver, "
                    + "alert_status, alert_status_type, ack_status, ack_status_type, "
                    + "alert_content, triggered_by, created_at) "
                    + "VALUES ('ALERT-ISSUE-PLAIN', 'ISSUE-WAIT-PAY-ISSUE-PLAIN', "
                    + "'PAY-ISSUE-PLAIN', '待回调未收口', 'P1', '支付运营', 'IN_APP_OUTBOX', "
                    + "'本地值班', '本地回归', 'success', '待确认', 'warn', '本地回归', 'TEST', NOW())");

            for (String fragment : Arrays.asList("%", "_", "!", "\\", suffix)) {
                PaymentIssueQueryDTO issueQuery = new PaymentIssueQueryDTO();
                issueQuery.setPaymentMethod("本地隔离方式");
                issueQuery.setPaymentOrderId(fragment);
                assertIssueResults(mapper, issueQuery, issueNo);
                issueQuery.setPaymentOrderId(null);
                issueQuery.setOrderNo(fragment);
                assertIssueResults(mapper, issueQuery, issueNo);
                issueQuery.setOrderNo(null);
                issueQuery.setChannelCode(fragment);
                assertIssueResults(mapper, issueQuery, issueNo);

                PaymentIssueAlertLogQueryDTO alertQuery = new PaymentIssueAlertLogQueryDTO();
                alertQuery.setAlertStatus("本地回归");
                alertQuery.setAlertNo(fragment);
                assertAlertResults(mapper, alertQuery, alertNo);
                alertQuery.setAlertNo(null);
                alertQuery.setIssueNo(fragment);
                assertAlertResults(mapper, alertQuery, alertNo);
                alertQuery.setIssueNo(null);
                alertQuery.setPaymentOrderId(fragment);
                assertAlertResults(mapper, alertQuery, alertNo);
            }
        }
    }

    private void assertIssueResults(PaymentIssueMapper mapper, PaymentIssueQueryDTO query, String issueNo) {
        assertEquals(1L, mapper.count(query), query.toString());
        assertEquals(issueNo, mapper.findAll(query).get(0).getIssueNo());
        assertEquals(1L, mapper.responsibilitySummary(query).stream()
                .mapToLong(summary -> summary.getTotalCount()).sum());
    }

    private void assertAlertResults(PaymentIssueMapper mapper, PaymentIssueAlertLogQueryDTO query,
                                    String alertNo) {
        assertEquals(1L, mapper.countAlertLogs(query), query.toString());
        assertEquals(alertNo, mapper.findAlertLogs(query).get(0).getAlertNo());
    }
}
