package com.abc123.hsp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundCallbackSourceDTO;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.dto.RefundChannelSubmitRequestDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.service.RefundChannelQueryAdapter;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.abc123.hsp.service.RefundService;
import java.math.BigDecimal;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class RefundSynchronousSubmitQueryIntegrationTest {

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
    void shouldReconcileSynchronousSuccessOnlyAfterQueryingSameAttempt() {
        String databaseUrl = "jdbc:h2:mem:refund_sync_query_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(
                HomeServicePaymentApplication.class, SynchronousChannelConfiguration.class)
                .profiles("demo", "refund-sync-query-test")
                .properties("payment.scheduling.enabled=false",
                        "spring.rabbitmq.listener.simple.auto-startup=false",
                        "spring.rabbitmq.listener.direct.auto-startup=false",
                        "logging.level.root=ERROR")
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + databaseUrl)) {
            RefundService refundService = context.getBean(RefundService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            RefundApplyRequestDTO application = new RefundApplyRequestDTO();
            application.setPaymentOrderId("PAY202607180074");
            application.setIdempotencyKey("demo-sync-success-query");
            application.setRefundAmount(new BigDecimal("20.00"));
            application.setRefundMethod("原路退款");
            application.setOperator("demo-applicant");
            String refundOrderId = refundService.apply(application).getRefundOrderId();

            RefundActionRequestDTO approval = new RefundActionRequestDTO();
            approval.setRefundOrderId(refundOrderId);
            approval.setOperator("demo-approver");
            assertEquals("PROCESSING", refundService.approve(approval).getStatus());
            RefundDetailDTO pending = refundService.detail(refundOrderId);
            assertEquals("UNKNOWN", pending.getAttempts().get(0).getAttemptStatus());
            assertNull(pending.getChannelRefundNo());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_type = 'REFUND_SUBMIT' "
                            + "AND biz_no = ? AND publish_status = 'UNKNOWN'",
                    Integer.class, pending.getAttempts().get(0).getRefundAttemptNo()));
            assertThrows(IllegalArgumentException.class, () -> refundService.retry(approval));
            assertEquals(1, refundService.detail(refundOrderId).getAttempts().size());

            RefundStatusQueryRequestDTO query = new RefundStatusQueryRequestDTO();
            query.setRefundOrderId(refundOrderId);
            query.setOperator("demo-query-operator");
            assertEquals("SUCCESS", refundService.query(query).getStatus());

            RefundDetailDTO settled = refundService.detail(refundOrderId);
            assertEquals("SUCCESS", settled.getAttempts().get(0).getAttemptStatus());
            assertEquals("RCH-" + pending.getAttempts().get(0).getRefundAttemptNo(),
                    settled.getChannelRefundNo());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_type = 'REFUND_SUBMIT' "
                            + "AND biz_no = ? AND publish_status = 'SUCCESS'",
                    Integer.class, pending.getAttempts().get(0).getRefundAttemptNo()));
            assertEquals("SUCCESS", refundService.query(query).getStatus());
            assertEquals(1, refundService.detail(refundOrderId).getAttempts().size());
        }
    }

    @Configuration
    @Profile("refund-sync-query-test")
    static class SynchronousChannelConfiguration {

        @Bean
        @Primary
        RefundChannelSubmitService synchronousRefundSubmitService() {
            return new RefundChannelSubmitService() {
                @Override
                public RefundChannelSubmitResultDTO submit(RefundChannelSubmitRequestDTO request) {
                    RefundChannelSubmitResultDTO result = new RefundChannelSubmitResultDTO();
                    result.setChannelRefundNo("RCH-" + request.getRefundAttemptNo());
                    result.setStatus("SUCCESS");
                    result.setStatusType("success");
                    return result;
                }
            };
        }

        @Bean
        @Order(-100)
        RefundChannelQueryAdapter synchronousRefundQueryAdapter() {
            return new RefundChannelQueryAdapter() {
                @Override
                public boolean supports(String channelCode) {
                    return "offline_bank".equals(channelCode);
                }

                @Override
                public RefundChannelQueryResultDTO query(RefundCallbackSourceDTO refund) {
                    assertNull(refund.getChannelRefundNo());
                    RefundChannelQueryResultDTO result = new RefundChannelQueryResultDTO();
                    result.setRefundStatus("SUCCESS");
                    result.setChannelRefundNo("RCH-" + refund.getRefundAttemptNo());
                    result.setQuerySource("TEST_CHANNEL_QUERY");
                    return result;
                }
            };
        }
    }
}
