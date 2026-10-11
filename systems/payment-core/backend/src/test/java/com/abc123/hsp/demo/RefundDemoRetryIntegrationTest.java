package com.abc123.hsp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundActionRequestDTO;
import com.abc123.hsp.dto.RefundApplyRequestDTO;
import com.abc123.hsp.dto.RefundCallbackRequestDTO;
import com.abc123.hsp.dto.RefundDetailDTO;
import com.abc123.hsp.service.RefundService;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class RefundDemoRetryIntegrationTest {

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
    void shouldPreserveAttemptIdentityAcrossFailedRefundRetry() throws Exception {
        String databaseUrl = "jdbc:h2:mem:refund_demo_retry_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(HomeServicePaymentApplication.class)
                .profiles("demo")
                .properties("payment.callback.require-signature=true",
                        "payment.scheduling.enabled=false",
                        "spring.rabbitmq.listener.simple.auto-startup=false",
                        "spring.rabbitmq.listener.direct.auto-startup=false",
                        "logging.level.root=ERROR")
                .web(WebApplicationType.NONE)
                .run("--spring.datasource.url=" + databaseUrl)) {
            RefundService refundService = context.getBean(RefundService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            RefundApplyRequestDTO application = new RefundApplyRequestDTO();
            application.setPaymentOrderId("PAY202607180074");
            application.setIdempotencyKey("demo-attempt-retry");
            application.setRefundAmount(new BigDecimal("20.00"));
            application.setRefundMethod("原路退款");
            application.setRefundReason("本地重试归属回归");
            application.setOperator("demo-applicant");
            String refundOrderId = refundService.apply(application).getRefundOrderId();

            RefundActionRequestDTO action = new RefundActionRequestDTO();
            action.setRefundOrderId(refundOrderId);
            action.setOperator("demo-approver");
            assertEquals("PROCESSING", refundService.approve(action).getStatus());
            RefundDetailDTO firstDetail = refundService.detail(refundOrderId);
            assertEquals(1, firstDetail.getAttempts().size());
            String firstAttemptNo = firstDetail.getAttempts().get(0).getRefundAttemptNo();
            String firstChannelRefundNo = firstDetail.getAttempts().get(0).getChannelRefundNo();
            assertEquals("ACCEPTED", firstDetail.getAttempts().get(0).getAttemptStatus());
            assertEquals("RCH-" + firstAttemptNo, firstChannelRefundNo);

            assertEquals("FAIL", refundService.callback("offline_bank", signedCallback(
                    refundOrderId, firstAttemptNo, firstChannelRefundNo, "FAIL")).getStatus());
            action.setOperator("demo-retry-operator");
            assertEquals("PROCESSING", refundService.retry(action).getStatus());

            RefundDetailDTO retried = refundService.detail(refundOrderId);
            assertEquals(2, retried.getAttempts().size());
            String nextAttemptNo = retried.getAttempts().get(0).getRefundAttemptNo();
            String nextChannelRefundNo = retried.getAttempts().get(0).getChannelRefundNo();
            assertNotEquals(firstAttemptNo, nextAttemptNo);
            assertEquals("RCH-" + nextAttemptNo, nextChannelRefundNo);
            assertNotEquals(firstChannelRefundNo, nextChannelRefundNo);
            assertEquals("ACCEPTED", retried.getAttempts().get(0).getAttemptStatus());
            assertEquals("FAIL", retried.getAttempts().get(1).getAttemptStatus());
            assertEquals(firstChannelRefundNo, retried.getAttempts().get(1).getChannelRefundNo());
            BusinessException previousAttempt = assertThrows(BusinessException.class,
                    () -> refundService.callback("offline_bank", signedCallback(
                            refundOrderId, firstAttemptNo, firstChannelRefundNo, "SUCCESS")));
            assertEquals(ErrorCode.REFUND_CALLBACK_TRANSACTION_MISMATCH, previousAttempt.getCode());
            assertEquals("PROCESSING", refundService.detail(refundOrderId).getStatus());
            assertEquals("SUCCESS", refundService.callback("offline_bank", signedCallback(
                    refundOrderId, nextAttemptNo, nextChannelRefundNo, "SUCCESS")).getStatus());
            RefundDetailDTO completed = refundService.detail(refundOrderId);
            assertEquals("SUCCESS", completed.getAttempts().get(0).getAttemptStatus());
            assertEquals("FAIL", completed.getAttempts().get(1).getAttemptStatus());
            assertEquals(nextChannelRefundNo, completed.getChannelRefundNo());
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_type = 'REFUND_SUBMIT' "
                            + "AND payment_order_id = 'PAY202607180074' AND publish_status = 'SUCCESS'",
                    Integer.class));
        }
    }

    private RefundCallbackRequestDTO signedCallback(
            String refundOrderId, String attemptNo, String channelRefundNo, String status) throws Exception {
        RefundCallbackRequestDTO request = new RefundCallbackRequestDTO();
        request.setRefundOrderId(refundOrderId);
        request.setRefundAttemptNo(attemptNo);
        request.setChannelRefundNo(channelRefundNo);
        request.setRefundStatus(status);
        request.setTimestamp(Long.toString(Instant.now().getEpochSecond()));
        request.setNonce(UUID.randomUUID().toString());
        String payload = String.join("|", "offline_bank", refundOrderId, attemptNo,
                status, channelRefundNo, request.getTimestamp(), request.getNonce());
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("offline-bank-secret-2026".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        request.setSignature(Base64.getEncoder().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8))));
        return request;
    }
}
