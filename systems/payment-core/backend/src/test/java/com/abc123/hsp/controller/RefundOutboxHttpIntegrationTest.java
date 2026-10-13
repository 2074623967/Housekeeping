package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import com.abc123.hsp.dto.RefundChannelSubmitResultDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundChannelSubmitService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.RefundChannelSubmitException;
import com.abc123.hsp.service.RefundChannelQueryService;
import com.abc123.hsp.service.impl.LocalRefundChannelSubmitServiceImpl;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class RefundOutboxHttpIntegrationTest {

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
    void shouldPersistUnknownRefundSubmitWhenManualRepublishReturnsFailure() throws Exception {
        RefundChannelSubmitService channelSubmitService = mock(RefundChannelSubmitService.class);
        when(channelSubmitService.submit(any()))
                .thenThrow(RefundChannelSubmitException.sanitized("local pre-channel rejection", false))
                .thenThrow(RefundChannelSubmitException.sanitized("local provider result unknown", true));
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("localRefundChannelSubmitServiceImpl");
            registry.registerSingleton("localRefundChannelSubmitServiceImpl", channelSubmitService);
        }));
        String databaseUrl = "jdbc:h2:mem:refund_manual_unknown_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-manual-unknown-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"测试未知受理\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            String attemptNo = jdbcTemplate.queryForObject(
                    "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = ?",
                    String.class, refundOrderId);
            String eventNo = jdbcTemplate.queryForObject(
                    "SELECT event_no FROM t_payment_event WHERE biz_no = ? AND event_type = 'REFUND_SUBMIT'",
                    String.class, attemptNo);
            assertEquals("FAILED", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));

            JsonNode republished = post(objectMapper, port,
                    "/api/payment-events/republish?paymentOrderId=" + paymentOrderId
                            + "&eventType=REFUND_SUBMIT",
                    "{\"eventNo\":\"" + eventNo + "\",\"operator\":\"refund-ops\"}");

            assertEquals("PAYMENT-400", republished.path("code").asText());
            assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));
            assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_order WHERE refund_order_id = ? "
                            + "AND channel_code IS NOT NULL AND channel_refund_no IS NULL",
                    Integer.class, refundOrderId));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_no = ? "
                            + "AND last_republish_operator IS NOT NULL",
                    Integer.class, eventNo));
            JsonNode blocked = post(objectMapper, port,
                    "/api/payment-events/republish?paymentOrderId=" + paymentOrderId
                            + "&eventType=REFUND_SUBMIT",
                    "{\"eventNo\":\"" + eventNo + "\",\"operator\":\"refund-ops\"}");
            assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED,
                    blocked.path("code").asText());
            verify(channelSubmitService, times(2)).submit(any());
        }
    }

    @Test
    void shouldRollBackApprovalWhenPersistedPaymentChannelHasTrailingWhitespace() throws Exception {
        PaymentEventDispatchService dispatchService = mock(PaymentEventDispatchService.class);
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("paymentEventDispatchServiceImpl");
            registry.registerSingleton("paymentEventDispatchServiceImpl", dispatchService);
        }));
        String databaseUrl = "jdbc:h2:mem:refund_padded_approval_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-padded-approval-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"测试异常渠道回滚\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_order SET channel_code = CONCAT(channel_code, ' ') "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET channel_code = CONCAT(channel_code, ' ') "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            assertTrue(Boolean.TRUE.equals(context.getBean(RefundMapper.class)
                    .findPaymentSourceForUpdate(paymentOrderId).getPaymentSuccessFactsValid()));

            JsonNode rejected = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");

            assertFalse("0".equals(rejected.path("code").asText()));
            assertEquals("REVIEWING", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_operation_log WHERE refund_order_id = ?",
                    Integer.class, refundOrderId));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?",
                    Integer.class, refundOrderId));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_type = 'REFUND_SUBMIT' "
                            + "AND payment_order_id = ?", Integer.class, paymentOrderId));
            org.mockito.Mockito.verifyNoInteractions(dispatchService);
        }
    }

    @Test
    void shouldRejectStaleNonceTtlForExistingEnabledChannelAtStartup() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:refund_stale_callback_ttl_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";

        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.callback.require-signature=true",
                "--payment.callback.nonce-ttl-seconds=601",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false"));
        Throwable cause = exception;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        assertTrue(cause.getMessage().contains("nonce TTL"));
    }

    @Test
    void shouldHoldUnknownRefundSubmitUntilProviderQueryConfirms() throws Exception {
        RefundChannelSubmitService channelSubmitService = mock(RefundChannelSubmitService.class);
        RefundChannelQueryService channelQueryService = mock(RefundChannelQueryService.class);
        RefundChannelQueryResultDTO confirmed = new RefundChannelQueryResultDTO();
        confirmed.setChannelRefundNo("RCH-UNKNOWN-RECOVERED");
        confirmed.setRefundStatus("SUCCESS");
        confirmed.setQuerySource("ISOLATED_PROVIDER_SIMULATION");
        when(channelQueryService.query(any())).thenReturn(confirmed);
        when(channelSubmitService.submit(any()))
                .thenThrow(RefundChannelSubmitException.sanitized("local test response unknown", true));
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("localRefundChannelSubmitServiceImpl");
            registry.registerSingleton("localRefundChannelSubmitServiceImpl", channelSubmitService);
            registry.removeBeanDefinition("refundChannelQueryServiceImpl");
            registry.registerSingleton("refundChannelQueryServiceImpl", channelQueryService);
        }));
        String databaseUrl = "jdbc:h2:mem:refund_unknown_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-unknown-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"未知结果验证\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            String attemptNo = jdbcTemplate.queryForObject(
                    "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = ?",
                    String.class, refundOrderId);
            String eventNo = jdbcTemplate.queryForObject(
                    "SELECT event_no FROM t_payment_event WHERE biz_no = ? AND event_type = 'REFUND_SUBMIT'",
                    String.class, attemptNo);
            assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));
            assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            assertFalse(context.getBean(PaymentEventMapper.class).findDueFailedEventNos(100).contains(eventNo));

            JsonNode rejected = post(objectMapper, port, "/api/payment-events/republish",
                    "{\"eventNo\":\"" + eventNo + "\",\"operator\":\"refund-ops\"}");
            assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, rejected.path("code").asText());
            assertEquals("UNKNOWN", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_no = ? "
                            + "AND last_republish_operator IS NOT NULL", Integer.class, eventNo));
            verify(channelSubmitService).submit(any());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?", Integer.class,
                    refundOrderId));

            JsonNode queried = post(objectMapper, port, "/api/refunds/query",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-ops\"}");
            assertEquals("0", queried.path("code").asText());
            assertEquals("SUCCESS", queried.path("data").path("status").asText());
            assertEquals("RCH-UNKNOWN-RECOVERED", jdbcTemplate.queryForObject(
                    "SELECT channel_refund_no FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));
            assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_operation_log WHERE refund_order_id = ? "
                            + "AND action_code = 'CHANNEL_ACCEPTANCE_RECOVERED'", Integer.class,
                    refundOrderId));
            assertEquals("SUCCESS", post(objectMapper, port, "/api/refunds/query",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-ops\"}")
                    .path("data").path("status").asText());
            verify(channelSubmitService).submit(any());
            verify(channelQueryService).query(any());
        }
    }

    @Test
    void shouldRecoverPreChannelFailureThroughTargetedEventRepublish() throws Exception {
        RefundChannelSubmitService channelSubmitService = mock(RefundChannelSubmitService.class);
        RefundChannelSubmitResultDTO accepted = new RefundChannelSubmitResultDTO();
        accepted.setChannelRefundNo("RCH-RETRY-HTTP");
        accepted.setStatus("PROCESSING");
        accepted.setStatusType("warn");
        when(channelSubmitService.submit(any()))
                .thenThrow(RefundChannelSubmitException.sanitized("local test rejected before submit", false))
                .thenReturn(accepted);
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("localRefundChannelSubmitServiceImpl");
            registry.registerSingleton("localRefundChannelSubmitServiceImpl", channelSubmitService);
        }));
        String databaseUrl = "jdbc:h2:mem:refund_republish_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-republish-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"本地重发验证\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            String attemptNo = jdbcTemplate.queryForObject(
                    "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = ?",
                    String.class, refundOrderId);
            String eventNo = jdbcTemplate.queryForObject(
                    "SELECT event_no FROM t_payment_event WHERE biz_no = ? AND event_type = 'REFUND_SUBMIT'",
                    String.class, attemptNo);
            assertEquals("FAILED", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?", String.class, eventNo));
            assertEquals("FAILED", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_attempt SET channel_code = CONCAT(channel_code, ' ') "
                            + "WHERE refund_attempt_no = ?", attemptNo));
            int retryCountBefore = jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM t_payment_event WHERE event_no = ?",
                    Integer.class, eventNo);
            JsonNode paddedRepublish = post(objectMapper, port,
                    "/api/payment-events/republish?paymentOrderId=" + paymentOrderId
                            + "&eventType=REFUND_SUBMIT",
                    "{\"eventNo\":\"" + eventNo + "\",\"operator\":\"refund-ops\"}");
            assertEquals("PAYMENT-400", paddedRepublish.path("code").asText());
            assertFalse("SUCCESS".equals(jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?",
                    String.class, eventNo)));
            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT retry_count FROM t_payment_event WHERE event_no = ?",
                    Integer.class, eventNo) > retryCountBefore);
            assertEquals("FAILED", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            verify(channelSubmitService, times(1)).submit(any());
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_attempt SET channel_code = TRIM(channel_code) "
                            + "WHERE refund_attempt_no = ?", attemptNo));

            JsonNode republished = post(objectMapper, port, "/api/payment-events/republish?paymentOrderId="
                    + paymentOrderId + "&eventType=REFUND_SUBMIT",
                    "{\"eventNo\":\"" + eventNo + "\",\"operator\":\"refund-ops\"}");
            assertEquals("0", republished.path("code").asText());
            assertEquals(1, republished.path("data").path("total").asInt());
            assertEquals("SUCCESS", republished.path("data").path("items").get(0)
                    .path("publishStatus").asText());
            assertEquals("refund-ops", jdbcTemplate.queryForObject(
                    "SELECT last_republish_operator FROM t_payment_event WHERE event_no = ?",
                    String.class, eventNo));
            assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals("RCH-RETRY-HTTP", jdbcTemplate.queryForObject(
                    "SELECT channel_refund_no FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            verify(channelSubmitService, times(2)).submit(any());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?", Integer.class,
                    refundOrderId));

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_attempt SET channel_code = CONCAT(channel_code, ' ') "
                            + "WHERE refund_attempt_no = ?", attemptNo));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_order SET channel_code = CONCAT(channel_code, ' ') "
                            + "WHERE refund_order_id = ?", refundOrderId));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_event SET publish_status = 'FAILED' WHERE event_no = ?", eventNo));
            assertFalse(context.getBean(PaymentEventDispatchService.class)
                    .republish(eventNo, "refund-ops"));
            assertEquals("DEAD_LETTER", jdbcTemplate.queryForObject(
                    "SELECT publish_status FROM t_payment_event WHERE event_no = ?",
                    String.class, eventNo));
            assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            verify(channelSubmitService, times(2)).submit(any());
        }
    }

    @Test
    void shouldAcceptDemoChannelSubmitAndFinalizeRefundCallback() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:refund_demo_lifecycle_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.callback.require-signature=true",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            assertEquals(LocalRefundChannelSubmitServiceImpl.class,
                    context.getBean(RefundChannelSubmitService.class).getClass());
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607180074";

            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-demo-lifecycle-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"本地模拟退款\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            assertEquals("REVIEWING", applied.path("data").path("status").asText());

            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            assertEquals("PROCESSING", approved.path("data").path("status").asText());
            String attemptNo = jdbcTemplate.queryForObject(
                    "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = ?",
                    String.class, refundOrderId);
            String channelRefundNo = "RCH-" + attemptNo;
            assertEquals("ACCEPTED", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals(channelRefundNo, jdbcTemplate.queryForObject(
                    "SELECT channel_refund_no FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            String eventNo = jdbcTemplate.queryForObject(
                    "SELECT event_no FROM t_payment_event WHERE biz_no = ? AND event_type = 'REFUND_SUBMIT' "
                            + "AND payment_order_id = ? AND publish_status = 'SUCCESS'",
                    String.class, attemptNo, paymentOrderId);
            assertEquals("SUCCESS", get(objectMapper, port, "/api/payment-events/" + eventNo)
                    .path("data").path("publishStatus").asText());

            String callbackBody = "{\"refundOrderId\":\"" + refundOrderId
                    + "\",\"refundAttemptNo\":\"" + attemptNo
                    + "\",\"channelRefundNo\":\"" + channelRefundNo
                    + "\",\"refundStatus\":\"SUCCESS\"}";
            assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID,
                    post(objectMapper, port, "/api/refunds/callback/offline_bank", callbackBody, 503)
                            .path("code").asText());
            assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            String ambiguousNonce = UUID.randomUUID() + "|callback";
            assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID,
                    post(objectMapper, port, "/api/refunds/callback/offline_bank",
                            signedRefundCallback(refundOrderId, attemptNo,
                                    channelRefundNo, ambiguousNonce), 503).path("code").asText());
            assertEquals("PROCESSING", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_refund_order WHERE refund_order_id = ?",
                    String.class, refundOrderId));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_callback_nonce WHERE channel_code = 'offline_bank' "
                            + "AND nonce = ?", Integer.class, ambiguousNonce));
            String signedCallback = signedRefundCallback(
                    refundOrderId, attemptNo, channelRefundNo, UUID.randomUUID().toString());
            assertEquals(ErrorCode.PAYMENT_CALLBACK_SIGNATURE_INVALID,
                    post(objectMapper, port, "/api/refunds/callback/offline_bank",
                            signedCallback.replace("\"refundStatus\":\"SUCCESS\"",
                                    "\"refundStatus\":\"FAIL\""), 503)
                            .path("code").asText());
            JsonNode callback = post(objectMapper, port,
                    "/api/refunds/callback/offline_bank", signedCallback);
            assertEquals("0", callback.path("code").asText());
            assertEquals("SUCCESS", callback.path("data").path("status").asText());
            assertEquals("SUCCESS", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_refund_attempt WHERE refund_attempt_no = ?",
                    String.class, attemptNo));
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_order WHERE refund_order_id = ? "
                            + "AND status = 'SUCCESS' AND terminal_at IS NOT NULL", Integer.class,
                    refundOrderId));
            assertEquals(ErrorCode.PAYMENT_CALLBACK_NONCE_REPLAY, post(objectMapper, port,
                    "/api/refunds/callback/offline_bank", signedCallback, 503).path("code").asText());
            assertEquals("SUCCESS", post(objectMapper, port,
                    "/api/refunds/callback/offline_bank",
                    signedRefundCallback(refundOrderId, attemptNo, channelRefundNo, UUID.randomUUID().toString()))
                    .path("data").path("status").asText());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?", Integer.class,
                    refundOrderId));
        }
    }

    private String signedRefundCallback(String refundOrderId, String attemptNo,
                                        String channelRefundNo, String nonce) throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String payload = String.join("|", "offline_bank", refundOrderId, attemptNo,
                "SUCCESS", channelRefundNo, timestamp, nonce);
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec("offline-bank-secret-2026".getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"));
        String signature = Base64.getEncoder().encodeToString(
                mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
        return "{\"refundOrderId\":\"" + refundOrderId
                + "\",\"refundAttemptNo\":\"" + attemptNo
                + "\",\"channelRefundNo\":\"" + channelRefundNo
                + "\",\"refundStatus\":\"SUCCESS\",\"timestamp\":\"" + timestamp
                + "\",\"nonce\":\"" + nonce
                + "\",\"signature\":\"" + signature + "\"}";
    }

    @Test
    void shouldReplaySameRefundApplicationAfterClientLosesResponse() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:refund_apply_replay_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            String idempotencyKey = "refund-apply-replay-http";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            String request = "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                    + "\"idempotencyKey\":\"" + idempotencyKey + "\",\"refundAmount\":12,"
                    + "\"refundMethod\":\"原路退款\",\"refundReason\":\"服务取消\","
                    + "\"operator\":\"refund-applicant\"}";
            JsonNode first = post(objectMapper, port, "/api/refunds/apply", request);
            assertEquals("0", first.path("code").asText());
            String refundOrderId = first.path("data").path("refundOrderId").asText();
            assertTrue(refundOrderId.startsWith("REF"));
            assertEquals("原路退款", first.path("data").path("refundMethod").asText());

            JsonNode replay = post(objectMapper, port, "/api/refunds/apply", request);
            assertEquals("0", replay.path("code").asText());
            assertEquals(refundOrderId, replay.path("data").path("refundOrderId").asText());
            assertEquals("REVIEWING", replay.path("data").path("status").asText());
            assertEquals("原路退款", replay.path("data").path("refundMethod").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_order WHERE payment_order_id = ? AND idempotency_key = ?",
                    Integer.class, paymentOrderId, idempotencyKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_operation_log WHERE refund_order_id = ? AND action_code = 'APPLY'",
                    Integer.class, refundOrderId));

            JsonNode conflict = post(objectMapper, port, "/api/refunds/apply",
                    request.replace("\"refundAmount\":12", "\"refundAmount\":13"));
            assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_CONFLICT,
                    conflict.path("code").asText());
            JsonNode differentMethod = post(objectMapper, port, "/api/refunds/apply",
                    request.replace("\"refundMethod\":\"原路退款\"", "\"refundMethod\":\"线下打款\""));
            assertEquals(ErrorCode.REFUND_APPLICATION_IDEMPOTENCY_CONFLICT,
                    differentMethod.path("code").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_order WHERE payment_order_id = ? AND idempotency_key = ?",
                    Integer.class, paymentOrderId, idempotencyKey));

            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            assertEquals("PROCESSING", approved.path("data").path("status").asText());
            JsonNode afterApproval = post(objectMapper, port, "/api/refunds/apply", request);
            assertEquals("0", afterApproval.path("code").asText());
            assertEquals(refundOrderId, afterApproval.path("data").path("refundOrderId").asText());
            assertEquals("PROCESSING", afterApproval.path("data").path("status").asText());
            assertEquals("原路退款", afterApproval.path("data").path("refundMethod").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_order WHERE payment_order_id = ? AND idempotency_key = ?",
                    Integer.class, paymentOrderId, idempotencyKey));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?",
                    Integer.class, refundOrderId));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE event_type = 'REFUND_SUBMIT' "
                            + "AND payment_order_id = ?", Integer.class, paymentOrderId));
        }
    }

    @Test
    void shouldCreateAndExposeMaskedRefundSubmitThroughApplyAndApprove() throws Exception {
        PaymentEventDispatchService dispatchService = mock(PaymentEventDispatchService.class);
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("paymentEventDispatchServiceImpl");
            registry.registerSingleton("paymentEventDispatchServiceImpl", dispatchService);
        }));
        String databaseUrl = "jdbc:h2:mem:refund_outbox_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            JsonNode applied = post(objectMapper, port, "/api/refunds/apply",
                    "{\"paymentOrderId\":\"" + paymentOrderId + "\","
                            + "\"idempotencyKey\":\"refund-outbox-http\",\"refundAmount\":20.00,"
                            + "\"refundMethod\":\"原路退款\",\"refundReason\":\"退款事件验证\","
                            + "\"operator\":\"refund-applicant\"}");
            assertEquals("0", applied.path("code").asText());
            assertEquals("REVIEWING", applied.path("data").path("status").asText());
            String refundOrderId = applied.path("data").path("refundOrderId").asText();
            assertTrue(refundOrderId.startsWith("REF"));

            JsonNode approved = post(objectMapper, port, "/api/refunds/approve",
                    "{\"refundOrderId\":\"" + refundOrderId + "\",\"operator\":\"refund-approver\"}");
            assertEquals("0", approved.path("code").asText());
            assertEquals("PROCESSING", approved.path("data").path("status").asText());
            assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_refund_attempt WHERE refund_order_id = ?", Integer.class,
                    refundOrderId));
            String attemptNo = jdbcTemplate.queryForObject(
                    "SELECT refund_attempt_no FROM t_refund_attempt WHERE refund_order_id = ?",
                    String.class, refundOrderId);
            String eventNo = jdbcTemplate.queryForObject(
                    "SELECT event_no FROM t_payment_event WHERE biz_no = ? AND event_type = 'REFUND_SUBMIT' "
                            + "AND payment_order_id = ? AND publish_status = 'PENDING'",
                    String.class, attemptNo, paymentOrderId);
            verify(dispatchService).publishRefundSubmit(eq(eventNo), eq(attemptNo), eq("refund-approver"));

            JsonNode eventDetail = get(objectMapper, port, "/api/payment-events/" + eventNo).path("data");
            assertEquals(eventNo, eventDetail.path("eventNo").asText());
            assertTrue(eventDetail.path("standardOrderNo").isNull());
            assertEquals("[REDACTED]", eventDetail.path("eventPayload").asText());
            JsonNode events = get(objectMapper, port, "/api/payment-events?paymentOrderId="
                    + paymentOrderId + "&eventType=REFUND_SUBMIT").path("data");
            assertEquals(1, events.path("total").asInt());
            assertEquals(eventNo, events.path("items").get(0).path("eventNo").asText());
            assertEquals("[REDACTED]", events.path("items").get(0).path("eventPayload").asText());

            JsonNode flows = get(objectMapper, port, "/api/payment-flows?paymentOrderId="
                    + paymentOrderId + "&flowSource=BUSINESS_EVENT").path("data");
            boolean foundRefundFlow = false;
            for (JsonNode item : flows.path("items")) {
                if (eventNo.equals(item.path("flowNo").asText())) {
                    assertEquals("[REDACTED]", item.path("requestPayload").asText());
                    foundRefundFlow = true;
                }
            }
            assertTrue(foundRefundFlow);
            JsonNode flowDetail = get(objectMapper, port,
                    "/api/payment-flows/BUSINESS_EVENT/" + eventNo).path("data");
            assertEquals(eventNo, flowDetail.path("flowNo").asText());
            assertTrue(flowDetail.path("standardOrderNo").isNull());
            assertEquals("[REDACTED]", flowDetail.path("requestPayload").asText());
        }
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String path, String body) throws Exception {
        return post(objectMapper, port, path, body, 200);
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String path, String body,
            int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(expectedStatus, connection.getResponseCode());
            try (InputStream response = expectedStatus >= 400
                    ? connection.getErrorStream() : connection.getInputStream()) {
                return objectMapper.readTree(response);
            }
        } finally {
            connection.disconnect();
        }
    }

    private JsonNode get(ObjectMapper objectMapper, int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        try {
            assertEquals(200, connection.getResponseCode());
            try (InputStream response = connection.getInputStream()) {
                return objectMapper.readTree(response);
            }
        } finally {
            connection.disconnect();
        }
    }
}
