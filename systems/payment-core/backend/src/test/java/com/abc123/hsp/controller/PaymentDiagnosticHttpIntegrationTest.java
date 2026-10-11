package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentListQueryDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentDiagnosticHttpIntegrationTest {

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
    void shouldExposePersistedPendingCallbackRequestAcrossDiagnosticsOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:payment_request_callback_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String query = "paymentOrderId=PAY202607190002&requestStatus=%E7%AD%89%E5%BE%85%E5%9B%9E%E8%B0%83";
            assertEquals("等待回调", get(objectMapper, port, "/api/payment-requests?" + query)
                    .path("data").path("items").get(0).path("requestStatus").asText());
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_attempt SET attempt_status = '待回调' "
                    + "WHERE attempt_no = 'ATT202607190002'"));

            JsonNode overview = get(objectMapper, port, "/api/payment-requests/overview?" + query)
                    .path("data");
            assertEquals(1, overview.path("totalRequestCount").asInt());
            assertEquals(1, overview.path("waitingCallbackRequestCount").asInt());
            assertEquals(0, overview.path("processingRequestCount").asInt());
            JsonNode page = get(objectMapper, port, "/api/payment-requests?" + query).path("data");
            assertEquals(1, page.path("total").asInt());
            assertEquals("待回调", page.path("items").get(0).path("requestStatus").asText());
            assertEquals("warn", page.path("items").get(0).path("requestStatusType").asText());
            JsonNode detail = get(objectMapper, port, "/api/payment-requests/ATT202607190002")
                    .path("data");
            assertEquals("待回调", detail.path("requestStatus").asText());
            assertTrue(getCsv(port, "/api/payment-requests/export?" + query).contains("\"待回调\""));
        }
    }

    @Test
    void shouldListInvalidSuccessFactsButRejectDetailOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:payment_diagnostic_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            PaymentListQueryDTO mapperQuery = new PaymentListQueryDTO();
            mapperQuery.setPaymentOrderId(paymentOrderId);
            PaymentMapper paymentMapper = context.getBean(PaymentMapper.class);
            assertTrustedSuccessMetrics(objectMapper, port, 1, "¥3,600.00");
            assertTrustedRefundRecord(objectMapper, port, 1, "¥600.00");
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_attempt SET completed_at = TIMESTAMP '2026-07-18 19:03:26.001' "
                            + "WHERE refund_order_id = 'REF202607180019'"));
            assertTrustedRefundRecord(objectMapper, port, 0, "¥0.00");
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_refund_attempt SET completed_at = TIMESTAMP '2026-07-18 19:03:26' "
                            + "WHERE refund_order_id = 'REF202607180019'"));
            assertTrustedRefundRecord(objectMapper, port, 1, "¥600.00");

            JsonNode seededFlow = get(objectMapper, port,
                    "/api/payment-flows/PAYMENT_ATTEMPT/ATT202607190001").path("data");
            assertEquals("2026-07-19 09:21:23", seededFlow.path("completedAt").asText());
            assertEquals("SUCCESS_FACT_MISMATCH", seededFlow.path("successFactIssue").asText());
            JsonNode seededRequest = get(objectMapper, port,
                    "/api/payment-requests/ATT202607190001").path("data");
            assertEquals("2026-07-19 09:21:23", seededRequest.path("completedAt").asText());
            assertEquals("SUCCESS_FACT_MISMATCH", seededRequest.path("successFactIssue").asText());
            assertEquals("SUCCESS_FACT_MISMATCH", get(objectMapper, port,
                    "/api/payment-routes/RTR202607190001").path("data")
                    .path("successFactIssue").asText());

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            assertTrustedSuccessMetrics(objectMapper, port, 2, "¥3,868.00");
            assertEquals("0", get(objectMapper, port, "/api/payments/" + paymentOrderId).path("code").asText());
            assertNull(paymentMapper.findAll(mapperQuery).get(0).getSuccessFactIssue());
            assertTrue(get(objectMapper, port, "/api/payments?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("successFactIssue").isNull());
            assertTrue(get(objectMapper, port, "/api/payment-logs?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("successFactIssue").isNull());
            assertPaymentFlowIssue(objectMapper, port, paymentOrderId, null);
            assertPaymentRequestIssue(objectMapper, port, paymentOrderId, null);
            assertPaymentRouteIssue(objectMapper, port, paymentOrderId, null);
            assertPaymentEventIssue(objectMapper, port, paymentOrderId, null);
            assertPaymentRecordIssue(objectMapper, port, paymentOrderId, null);
            String normalCsv = getCsv(port, "/api/payments/export?paymentOrderId=" + paymentOrderId);
            assertTrue(normalCsv.startsWith("\uFEFF支付单号,业务链路"));
            assertTrue(normalCsv.contains("创建时间,成功事实异常类型,预付归属异常类型\n"));
            assertTrue(normalCsv.endsWith(",\"\"\n"));

            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET amount = 267.00 "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertTrustedSuccessMetrics(objectMapper, port, 1, "¥3,600.00");
            JsonNode amountMismatch = get(objectMapper, port,
                    "/api/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=" + paymentOrderId);
            assertEquals(1, amountMismatch.path("data").path("total").asInt());
            assertEquals("SUCCESS_FACT_MISMATCH", amountMismatch.path("data")
                    .path("items").get(0).path("successFactIssue").asText());
            assertPaymentFlowIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            assertEquals(ErrorCode.SYSTEM_ERROR, get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("code").asText());
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET amount = 268.00 "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertTrustedSuccessMetrics(objectMapper, port, 2, "¥3,868.00");

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_attempt SET completed_at = TIMESTAMP '2026-07-19 09:21:22.001' "
                            + "WHERE payment_order_id = ?", paymentOrderId));
            assertTrustedSuccessMetrics(objectMapper, port, 1, "¥3,600.00");
            JsonNode mismatchPage = get(objectMapper, port,
                    "/api/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=" + paymentOrderId);
            assertEquals("0", mismatchPage.path("code").asText());
            assertEquals(1, mismatchPage.path("data").path("total").asInt());
            assertEquals(paymentOrderId, mismatchPage.path("data").path("items").get(0)
                    .path("paymentOrderId").asText());
            assertEquals("SUCCESS_FACT_MISMATCH", mismatchPage.path("data").path("items").get(0)
                    .path("successFactIssue").asText());
            assertEquals("SUCCESS_FACT_MISMATCH", paymentMapper.findAll(mapperQuery).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_FACT_MISMATCH", paymentMapper.findAllForExport(mapperQuery)
                    .get(0).getSuccessFactIssue());
            assertTrue(getCsv(port, "/api/payments/export?paymentOrderId=" + paymentOrderId)
                    .endsWith(",\"SUCCESS_FACT_MISMATCH\",\"\"\n"));
            for (String status : new String[] { "全部", "SUCCESS" }) {
                JsonNode ordinaryPage = get(objectMapper, port,
                        "/api/payments?status=" + ("全部".equals(status) ? "%E5%85%A8%E9%83%A8" : status)
                                + "&paymentOrderId=" + paymentOrderId);
                assertEquals("SUCCESS_FACT_MISMATCH", ordinaryPage.path("data").path("items").get(0)
                        .path("successFactIssue").asText());
            }
            JsonNode logs = get(objectMapper, port,
                    "/api/payment-logs?paymentOrderId=" + paymentOrderId);
            assertEquals("0", logs.path("code").asText());
            assertTrue(logs.path("data").path("total").asInt() > 0);
            for (JsonNode item : logs.path("data").path("items")) {
                assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
                assertEquals("SUCCESS_FACT_MISMATCH", item.path("successFactIssue").asText());
            }
            assertEquals("SUCCESS_FACT_MISMATCH", get(objectMapper, port,
                    "/api/payment-logs/ATT202607190001").path("data").path("successFactIssue").asText());
            String mismatchLogCsv = getCsv(port, "/api/payment-logs/export?paymentOrderId=" + paymentOrderId);
            assertTrue(mismatchLogCsv.contains("创建时间,支付单成功事实异常类型\n"));
            assertTrue(mismatchLogCsv.endsWith(",\"SUCCESS_FACT_MISMATCH\"\n"));
            assertPaymentFlowIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            assertPaymentRequestIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            assertPaymentRouteIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            assertPaymentEventIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            assertPaymentRecordIssue(objectMapper, port, paymentOrderId, "SUCCESS_FACT_MISMATCH");
            JsonNode mismatchDetail = get(objectMapper, port, "/api/payments/" + paymentOrderId);
            assertEquals(ErrorCode.SYSTEM_ERROR, mismatchDetail.path("code").asText());
            assertTrue(mismatchDetail.path("data").isNull());

            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_order SET success_at = NULL WHERE payment_order_id = ?", paymentOrderId));
            assertTrustedSuccessMetrics(objectMapper, port, 1, "¥3,600.00");
            JsonNode missingTimePage = get(objectMapper, port,
                    "/api/payments?status=SUCCESS_TIME_MISSING&paymentOrderId=" + paymentOrderId);
            assertEquals("0", missingTimePage.path("code").asText());
            assertEquals(1, missingTimePage.path("data").path("total").asInt());
            assertEquals(paymentOrderId, missingTimePage.path("data").path("items").get(0)
                    .path("paymentOrderId").asText());
            assertEquals("SUCCESS_TIME_MISSING", missingTimePage.path("data").path("items").get(0)
                    .path("successFactIssue").asText());
            assertEquals("SUCCESS_TIME_MISSING", paymentMapper.findAll(mapperQuery).get(0).getSuccessFactIssue());
            assertEquals("SUCCESS_TIME_MISSING", get(objectMapper, port,
                    "/api/payments?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("successFactIssue").asText());
            assertEquals("SUCCESS_TIME_MISSING", get(objectMapper, port,
                    "/api/payment-logs?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("successFactIssue").asText());
            assertEquals("SUCCESS_TIME_MISSING", get(objectMapper, port,
                    "/api/payment-logs/ATT202607190001").path("data").path("successFactIssue").asText());
            assertTrue(getCsv(port, "/api/payment-logs/export?paymentOrderId=" + paymentOrderId)
                    .endsWith(",\"SUCCESS_TIME_MISSING\"\n"));
            assertPaymentFlowIssue(objectMapper, port, paymentOrderId, "SUCCESS_TIME_MISSING");
            assertPaymentRequestIssue(objectMapper, port, paymentOrderId, "SUCCESS_TIME_MISSING");
            assertPaymentRouteIssue(objectMapper, port, paymentOrderId, "SUCCESS_TIME_MISSING");
            assertPaymentEventIssue(objectMapper, port, paymentOrderId, "SUCCESS_TIME_MISSING");
            assertPaymentRecordIssue(objectMapper, port, paymentOrderId, "SUCCESS_TIME_MISSING");
            assertTrue(getCsv(port, "/api/payments/export?status=SUCCESS_TIME_MISSING&paymentOrderId="
                    + paymentOrderId).endsWith(",\"SUCCESS_TIME_MISSING\",\"\"\n"));
            JsonNode missingTimeDetail = get(objectMapper, port, "/api/payments/" + paymentOrderId);
            assertEquals(ErrorCode.SYSTEM_ERROR, missingTimeDetail.path("code").asText());
            assertTrue(missingTimeDetail.path("data").isNull());

            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET success_at = "
                    + "TIMESTAMP '2026-07-19 09:21:22' WHERE payment_order_id = ?", paymentOrderId));
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_attempt SET completed_at = "
                    + "TIMESTAMP '2026-07-19 09:21:22' WHERE payment_order_id = ?", paymentOrderId));
            for (String amount : new String[]{"0.00", "-5.00"}) {
                assertEquals(1, jdbcTemplate.update(
                        "UPDATE t_payment_order SET amount = ? WHERE payment_order_id = ?",
                        new BigDecimal(amount), paymentOrderId));
                assertTrustedSuccessMetrics(objectMapper, port, 1, "¥3,600.00");
                JsonNode invalidAmountPage = get(objectMapper, port,
                        "/api/payments?status=SUCCESS_FACT_MISMATCH&paymentOrderId=" + paymentOrderId);
                assertEquals("0", invalidAmountPage.path("code").asText());
                assertEquals(1, invalidAmountPage.path("data").path("total").asInt());
                JsonNode invalidAmountRow = invalidAmountPage.path("data").path("items").get(0);
                assertEquals("¥" + amount, invalidAmountRow.path("amount").asText());
                assertEquals("SUCCESS_FACT_MISMATCH", invalidAmountRow.path("successFactIssue").asText());
                assertTrue(getCsv(port, "/api/payments/export?status=SUCCESS_FACT_MISMATCH&paymentOrderId="
                        + paymentOrderId).contains("\"¥" + amount + "\""));
                JsonNode invalidAmountDetail = get(objectMapper, port, "/api/payments/" + paymentOrderId);
                assertEquals(ErrorCode.SYSTEM_ERROR, invalidAmountDetail.path("code").asText());
                assertTrue(invalidAmountDetail.path("data").isNull());
            }
        }
    }

    @Test
    void shouldServeMaskedLegacyRefundEventAcrossDiagnosticsOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:refund_event_diagnostic_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190001";
            String eventNo = "EVT-HTTP-REFUND-001";
            assertEquals(1, jdbcTemplate.update(
                    "INSERT INTO t_payment_event (event_no, event_type, payment_order_id, biz_no, "
                            + "downstream_system, event_topic, publish_status, publish_status_type, "
                            + "retry_count, next_retry_at, event_payload, created_at) "
                            + "VALUES (?, 'REFUND_SUBMIT', ?, 'ORD202607190001', 'gateway-access', "
                            + "'payment.refund.submitted.v1', 'PENDING', 'warn', 0, "
                            + "TIMESTAMP '2026-07-19 12:00:00', '退款渠道待下发', "
                            + "TIMESTAMP '2026-07-19 11:00:00')",
                    eventNo, paymentOrderId));

            JsonNode events = get(objectMapper, port,
                    "/api/payment-events?paymentOrderId=" + paymentOrderId
                            + "&eventType=REFUND_SUBMIT&pageNo=1&pageSize=20");
            assertEquals("0", events.path("code").asText());
            assertEquals(1, events.path("data").path("total").asInt());
            JsonNode eventRow = events.path("data").path("items").get(0);
            assertEquals(eventNo, eventRow.path("eventNo").asText());
            assertEquals("[REDACTED]", eventRow.path("eventPayload").asText());

            JsonNode eventDetail = get(objectMapper, port, "/api/payment-events/" + eventNo).path("data");
            assertEquals(eventNo, eventDetail.path("eventNo").asText());
            assertTrue(eventDetail.path("standardOrderNo").isNull());
            assertEquals("[REDACTED]", eventDetail.path("eventPayload").asText());

            JsonNode flows = get(objectMapper, port,
                    "/api/payment-flows?paymentOrderId=" + paymentOrderId
                            + "&flowSource=BUSINESS_EVENT&pageNo=1&pageSize=20");
            assertEquals("0", flows.path("code").asText());
            JsonNode refundFlow = null;
            for (JsonNode item : flows.path("data").path("items")) {
                if (eventNo.equals(item.path("flowNo").asText())) {
                    refundFlow = item;
                }
            }
            assertTrue(refundFlow != null);
            assertEquals("[REDACTED]", refundFlow.path("requestPayload").asText());

            JsonNode flowDetail = get(objectMapper, port,
                    "/api/payment-flows/BUSINESS_EVENT/" + eventNo).path("data");
            assertEquals(eventNo, flowDetail.path("flowNo").asText());
            assertTrue(flowDetail.path("standardOrderNo").isNull());
            assertEquals("[REDACTED]", flowDetail.path("requestPayload").asText());
        }
    }

    private void assertTrustedSuccessMetrics(ObjectMapper objectMapper, int port, int successCount,
                                             String successAmount) throws Exception {
        JsonNode dashboard = get(objectMapper, port, "/api/payment-metrics/summary").path("data");
        JsonNode monitor = get(objectMapper, port, "/api/payment-monitor/overview").path("data");
        assertEquals(3, dashboard.path("totalCount").asInt());
        assertEquals(successCount, dashboard.path("successCount").asInt());
        assertEquals(successAmount, dashboard.path("successAmount").asText());
        assertEquals(successCount == 1 ? "33.33%" : "66.67%", dashboard.path("successRate").asText());
        assertEquals(successCount, monitor.path("summary").path("successCount").asInt());
        assertEquals(successAmount, monitor.path("summary").path("successAmount").asText());
        assertEquals(dashboard.path("successRate").asText(), monitor.path("summary").path("successRate").asText());
        assertEquals(3, monitor.path("channelMetrics").size());
        for (JsonNode channel : monitor.path("channelMetrics")) {
            if ("offline_bank".equals(channel.path("channelCode").asText())) {
                assertEquals(1, channel.path("successCount").asInt());
            } else if ("wx_jsapi".equals(channel.path("channelCode").asText())) {
                assertEquals(successCount - 1, channel.path("successCount").asInt());
            }
        }
    }

    private void assertTrustedRefundRecord(ObjectMapper objectMapper, int port, int refundCount,
                                           String refundedAmount) throws Exception {
        String query = "businessOrderNo=ORD202607180118";
        JsonNode page = get(objectMapper, port, "/api/payment-records?" + query).path("data");
        assertEquals(1, page.path("total").asInt());
        JsonNode listed = page.path("items").get(0);
        JsonNode detail = get(objectMapper, port, "/api/payment-records/PAY202607180074").path("data");
        assertEquals(refundCount, listed.path("refundCount").asInt());
        assertEquals(refundedAmount, listed.path("refundedAmount").asText());
        assertEquals(1 - refundCount, listed.path("unverifiedRefundCount").asInt());
        assertEquals(refundCount, detail.path("refundCount").asInt());
        assertEquals(refundedAmount, detail.path("refundedAmount").asText());
        assertEquals(1 - refundCount, detail.path("unverifiedRefundCount").asInt());
        String csv = getCsv(port, "/api/payment-records/export?" + query);
        assertTrue(csv.contains(",\"" + refundCount + "\",\"" + refundedAmount + "\","));
        assertTrue(csv.endsWith(",\"\",\"" + (1 - refundCount) + "\",\"\"\n"));
    }

    private void assertPaymentFlowIssue(ObjectMapper objectMapper, int port, String paymentOrderId,
                                        String expectedIssue) throws Exception {
        JsonNode page = get(objectMapper, port, "/api/payment-flows?paymentOrderId=" + paymentOrderId);
        assertEquals("0", page.path("code").asText());
        assertTrue(page.path("data").path("total").asInt() > 0);
        for (JsonNode item : page.path("data").path("items")) {
            assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
            if (expectedIssue == null) {
                assertTrue(item.path("successFactIssue").isNull());
            } else {
                assertEquals(expectedIssue, item.path("successFactIssue").asText());
            }
        }
        JsonNode detail = get(objectMapper, port, "/api/payment-flows/PAYMENT_ATTEMPT/ATT202607190001")
                .path("data");
        if (expectedIssue == null) {
            assertTrue(detail.path("successFactIssue").isNull());
        } else {
            assertEquals(expectedIssue, detail.path("successFactIssue").asText());
        }
        String csv = getCsv(port, "/api/payment-flows/export?paymentOrderId=" + paymentOrderId);
        assertTrue(csv.contains("创建时间,支付单成功事实异常类型\n"));
        assertTrue(csv.endsWith(expectedIssue == null ? ",\"\"\n" : ",\"" + expectedIssue + "\"\n"));
    }

    private void assertPaymentRequestIssue(ObjectMapper objectMapper, int port, String paymentOrderId,
                                           String expectedIssue) throws Exception {
        JsonNode page = get(objectMapper, port, "/api/payment-requests?paymentOrderId=" + paymentOrderId);
        assertEquals("0", page.path("code").asText());
        assertTrue(page.path("data").path("total").asInt() > 0);
        for (JsonNode item : page.path("data").path("items")) {
            assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
            if (expectedIssue == null) {
                assertTrue(item.path("successFactIssue").isNull());
            } else {
                assertEquals(expectedIssue, item.path("successFactIssue").asText());
            }
        }
        JsonNode detail = get(objectMapper, port, "/api/payment-requests/ATT202607190001")
                .path("data");
        if (expectedIssue == null) {
            assertTrue(detail.path("successFactIssue").isNull());
        } else {
            assertEquals(expectedIssue, detail.path("successFactIssue").asText());
        }
        String csv = getCsv(port, "/api/payment-requests/export?paymentOrderId=" + paymentOrderId);
        assertTrue(csv.contains("响应报文,支付单成功事实异常类型\n"));
        assertTrue(csv.endsWith(expectedIssue == null ? ",\"\"\n" : ",\"" + expectedIssue + "\"\n"));
    }

    private void assertPaymentRecordIssue(ObjectMapper objectMapper, int port, String paymentOrderId,
                                          String expectedIssue) throws Exception {
        String query = "businessOrderNo=ORD202607190001";
        JsonNode page = get(objectMapper, port, "/api/payment-records?" + query);
        assertEquals("0", page.path("code").asText());
        assertEquals(1, page.path("data").path("total").asInt());
        JsonNode item = page.path("data").path("items").get(0);
        assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
        JsonNode detail = get(objectMapper, port, "/api/payment-records/" + paymentOrderId)
                .path("data");
        if (expectedIssue == null) {
            assertTrue(item.path("successFactIssue").isNull());
            assertTrue(detail.path("successFactIssue").isNull());
        } else {
            assertEquals(expectedIssue, item.path("successFactIssue").asText());
            assertEquals(expectedIssue, detail.path("successFactIssue").asText());
        }
        String csv = getCsv(port, "/api/payment-records/export?" + query);
        assertTrue(csv.contains("用户ID,支付单成功事实异常类型,待核查成功退款笔数,预付归属异常类型\n"));
        assertTrue(csv.endsWith(",\"" + (expectedIssue == null ? "" : expectedIssue) + "\",\"0\",\"\"\n"));
    }

    private void assertPaymentEventIssue(ObjectMapper objectMapper, int port, String paymentOrderId,
                                         String expectedIssue) throws Exception {
        JsonNode page = get(objectMapper, port, "/api/payment-events?paymentOrderId=" + paymentOrderId);
        assertEquals("0", page.path("code").asText());
        assertTrue(page.path("data").path("total").asInt() > 0);
        for (JsonNode item : page.path("data").path("items")) {
            assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
            if (expectedIssue == null) {
                assertTrue(item.path("successFactIssue").isNull());
            } else {
                assertEquals(expectedIssue, item.path("successFactIssue").asText());
            }
        }
        JsonNode detail = get(objectMapper, port, "/api/payment-events/EVT202607190001").path("data");
        if (expectedIssue == null) {
            assertTrue(detail.path("successFactIssue").isNull());
        } else {
            assertEquals(expectedIssue, detail.path("successFactIssue").asText());
        }
        String csv = getCsv(port, "/api/payment-events/export?paymentOrderId=" + paymentOrderId);
        assertTrue(csv.contains("创建时间,支付单成功事实异常类型\n"));
        assertTrue(csv.endsWith(expectedIssue == null ? ",\"\"\n" : ",\"" + expectedIssue + "\"\n"));
    }

    private void assertPaymentRouteIssue(ObjectMapper objectMapper, int port, String paymentOrderId,
                                         String expectedIssue) throws Exception {
        JsonNode page = get(objectMapper, port, "/api/payment-routes?paymentOrderId=" + paymentOrderId);
        assertEquals("0", page.path("code").asText());
        assertTrue(page.path("data").path("total").asInt() > 0);
        for (JsonNode item : page.path("data").path("items")) {
            assertEquals(paymentOrderId, item.path("paymentOrderId").asText());
            if (expectedIssue == null) {
                assertTrue(item.path("successFactIssue").isNull());
            } else {
                assertEquals(expectedIssue, item.path("successFactIssue").asText());
            }
        }
        JsonNode detail = get(objectMapper, port, "/api/payment-routes/RTR202607190001")
                .path("data");
        if (expectedIssue == null) {
            assertTrue(detail.path("successFactIssue").isNull());
        } else {
            assertEquals(expectedIssue, detail.path("successFactIssue").asText());
        }
        String csv = getCsv(port, "/api/payment-routes/export?paymentOrderId=" + paymentOrderId);
        assertTrue(csv.contains("创建时间,支付单成功事实异常类型\n"));
        assertTrue(csv.endsWith(expectedIssue == null ? ",\"\"\n" : ",\"" + expectedIssue + "\"\n"));
    }

    @Test
    void shouldNotExpireSubmittedCashierSessionOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:cashier_session_expiry_http_" + System.nanoTime()
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
            String sessionNo = "PRE202607190002";
            String paymentOrderId = "PAY202607190002";
            assertEquals("支付中", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("info", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatusType").asText());
            assertEquals("支付中", get(objectMapper, port, "/api/cashier-sessions?sessionNo=" + sessionNo)
                    .path("data").path("items").get(0).path("sessionStatus").asText());
            JsonNode overview = get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + sessionNo).path("data");
            assertEquals(1, overview.path("payingSessionCount").asInt());
            assertEquals(0, overview.path("expiredSessionCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + sessionNo)
                    .contains("支付中"));

            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET cashier_status = '待支付', "
                    + "cashier_status_type = 'warn', expires_at = TIMESTAMPADD(MINUTE, 5, NOW()) "
                    + "WHERE prepay_order_no = ?", sessionNo));
            assertEquals("支付中", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("info", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatusType").asText());
            assertEquals("支付中", get(objectMapper, port, "/api/cashier-sessions?sessionNo=" + sessionNo)
                    .path("data").path("items").get(0).path("sessionStatus").asText());
            JsonNode staleOverview = get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + sessionNo).path("data");
            assertEquals(1, staleOverview.path("payingSessionCount").asInt());
            assertEquals(0, staleOverview.path("pendingSessionCount").asInt());
            assertEquals(0, staleOverview.path("expiringSoonCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + sessionNo)
                    .contains("支付中"));

            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                    + "cashier_status_type = 'success' WHERE prepay_order_no = ?", sessionNo));
            assertEquals("支付中", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("info", get(objectMapper, port, "/api/cashier-sessions?sessionNo=" + sessionNo)
                    .path("data").path("items").get(0).path("sessionStatusType").asText());
            assertEquals(0, get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + sessionNo)
                    .path("data").path("successSessionCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + sessionNo)
                    .contains("\"支付中\",\"info\""));

            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET status = 'PREPAY_CREATED' "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order "
                    + "SET expires_at = TIMESTAMPADD(MINUTE, -1, NOW()) "
                    + "WHERE prepay_order_no = ?", sessionNo));
            assertEquals("已失效", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("danger", get(objectMapper, port,
                    "/api/cashier-sessions?sessionNo=" + sessionNo)
                    .path("data").path("items").get(0).path("sessionStatusType").asText());
            assertEquals(1, get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + sessionNo)
                    .path("data").path("expiredSessionCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + sessionNo)
                    .contains("\"已失效\",\"danger\""));
            JsonNode userCashier = get(objectMapper, port,
                    "/api/payments/cashier/" + sessionNo).path("data");
            assertEquals("PREPAY_CREATED", userCashier.path("paymentStatus").asText());
            assertEquals("已失效", userCashier.path("status").asText());
            assertEquals("danger", userCashier.path("statusType").asText());

            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET status = 'CLOSED' "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertEquals("已关闭", get(objectMapper, port, "/api/cashier-sessions/" + sessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("danger", get(objectMapper, port,
                    "/api/cashier-sessions?sessionNo=" + sessionNo + "&sessionStatus="
                            + java.net.URLEncoder.encode("已关闭", "UTF-8"))
                    .path("data").path("items").get(0).path("sessionStatusType").asText());
            JsonNode closedOverview = get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + sessionNo).path("data");
            assertEquals(0, closedOverview.path("successSessionCount").asInt());
            assertEquals(0, closedOverview.path("expiredSessionCount").asInt());
            assertEquals(1, closedOverview.path("otherSessionCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + sessionNo)
                    .contains("\"已关闭\",\"danger\""));

            String invalidSessionNo = "PRE202607190001";
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET cashier_status = '支付成功', "
                    + "cashier_status_type = 'success' WHERE prepay_order_no = ?", invalidSessionNo));
            assertEquals("成功事实异常", get(objectMapper, port, "/api/cashier-sessions/" + invalidSessionNo)
                    .path("data").path("sessionStatus").asText());
            assertEquals("danger", get(objectMapper, port,
                    "/api/cashier-sessions?sessionNo=" + invalidSessionNo + "&sessionStatus="
                            + java.net.URLEncoder.encode("成功事实异常", "UTF-8"))
                    .path("data").path("items").get(0).path("sessionStatusType").asText());
            JsonNode invalidOverview = get(objectMapper, port,
                    "/api/cashier-sessions/overview?sessionNo=" + invalidSessionNo).path("data");
            assertEquals(0, invalidOverview.path("successSessionCount").asInt());
            assertEquals(1, invalidOverview.path("otherSessionCount").asInt());
            assertTrue(getCsv(port, "/api/cashier-sessions/export?sessionNo=" + invalidSessionNo)
                    .contains("\"成功事实异常\",\"danger\""));

            JsonNode trustedSession = get(objectMapper, port,
                    "/api/cashier-sessions/PRE202607180118").path("data");
            assertEquals("已完成", trustedSession.path("sessionStatus").asText());
            assertEquals("success", trustedSession.path("sessionStatusType").asText());
        }
    }

    @Test
    void shouldSettleAdminQuerySuccessAcrossPaymentBillAndEventOverHttp() throws Exception {
        PaymentChannelQueryService channelQueryService = mock(PaymentChannelQueryService.class);
        PaymentEventDispatchService dispatchService = mock(PaymentEventDispatchService.class);
        PaymentChannelQueryResultDTO channelResult = new PaymentChannelQueryResultDTO();
        channelResult.setTradeStatus("SUCCESS");
        channelResult.setChannelTransactionNo("ALI77665544");
        channelResult.setQuerySource("LOCAL_HTTP_TEST");
        when(channelQueryService.query(any())).thenReturn(channelResult);
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("paymentChannelQueryServiceImpl");
            registry.registerSingleton("paymentChannelQueryServiceImpl", channelQueryService);
            registry.removeBeanDefinition("paymentEventDispatchServiceImpl");
            registry.registerSingleton("paymentEventDispatchServiceImpl", dispatchService);
        }));
        String databaseUrl = "jdbc:h2:mem:payment_admin_query_success_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190002";
            String body = "{\"paymentOrderId\":\"" + paymentOrderId
                    + "\",\"operator\":\"local-operator\"}";
            assertEquals("WAIT_CALLBACK", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());

            JsonNode settled = post(objectMapper, port, "/api/payments/admin/query", body, 200);
            assertEquals("0", settled.path("code").asText());
            assertEquals("SUCCESS", settled.path("data").path("status").asText());
            assertEquals("LOCAL_HTTP_TEST", settled.path("data").path("querySource").asText());
            assertEquals("SUCCESS", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());
            assertEquals("SUCCESS", get(objectMapper, port,
                    "/api/payments?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("status").asText());
            assertEquals("支付成功", jdbcTemplate.queryForObject(
                    "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = 'PRE202607190002'",
                    String.class));
            assertEquals("成功", jdbcTemplate.queryForObject(
                    "SELECT attempt_status FROM t_payment_attempt WHERE attempt_no = 'ATT202607190002'",
                    String.class));
            assertEquals("已结清", jdbcTemplate.queryForObject(
                    "SELECT bill_status FROM t_bill WHERE bill_no = 'BILL202607190002'",
                    String.class));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT paid_amount - bill_amount FROM t_bill WHERE bill_no = 'BILL202607190002'",
                    java.math.BigDecimal.class).signum());
            assertTrue(jdbcTemplate.queryForObject(
                    "SELECT success_at FROM t_payment_order WHERE payment_order_id = ?",
                    java.sql.Timestamp.class, paymentOrderId) != null);
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_notify_log WHERE payment_order_id = ? "
                            + "AND notify_type = 'SUCCESS' AND notify_payload LIKE ?",
                    Integer.class, paymentOrderId, "%local-operator%"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                            + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, paymentOrderId));
            JsonNode replay = post(objectMapper, port, "/api/payments/admin/query", body, 200);
            assertEquals("0", replay.path("code").asText());
            assertEquals("SUCCESS", replay.path("data").path("status").asText());
            JsonNode callbackReplay = post(objectMapper, port, "/api/payments/callback/alipay_h5",
                    "{\"paymentOrderId\":\"" + paymentOrderId
                            + "\",\"channelTransactionNo\":\"ALI77665544\",\"tradeStatus\":\"SUCCESS\"}", 200);
            assertEquals("0", callbackReplay.path("code").asText());
            assertEquals("SUCCESS", callbackReplay.path("data").path("status").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                            + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, paymentOrderId));
            String eventPayload = jdbcTemplate.queryForObject(
                    "SELECT event_payload FROM t_payment_event WHERE payment_order_id = ? "
                            + "AND event_type = 'PAYMENT_SUCCESS'", String.class, paymentOrderId);
            assertEquals("ACTIVE_QUERY", objectMapper.readTree(eventPayload).path("source").asText());
            assertEquals("local-operator", objectMapper.readTree(eventPayload).path("initiatedBy").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_notify_log WHERE payment_order_id = ? "
                            + "AND notify_type = 'SUCCESS'", Integer.class, paymentOrderId));
            verify(dispatchService, times(1)).publishPaymentSuccess(any(), eq(paymentOrderId));
        }
    }

    @Test
    void shouldReturnCallbackWinnerWhenQueryResponseArrivesAfterCallbackOverHttp() throws Exception {
        assertCallbackWinsDelayedQuery("ALI77665544", false);
    }

    @Test
    void shouldRejectConflictingQueryResponseAfterCallbackWinsOverHttp() throws Exception {
        assertCallbackWinsDelayedQuery("ALI-WRONG", true);
    }

    private void assertCallbackWinsDelayedQuery(String queriedTransactionNo, boolean conflicting) throws Exception {
        PaymentChannelQueryService channelQueryService = mock(PaymentChannelQueryService.class);
        PaymentEventDispatchService dispatchService = mock(PaymentEventDispatchService.class);
        CountDownLatch queryEntered = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);
        PaymentChannelQueryResultDTO channelResult = new PaymentChannelQueryResultDTO();
        channelResult.setTradeStatus("SUCCESS");
        channelResult.setChannelTransactionNo(queriedTransactionNo);
        channelResult.setQuerySource("LOCAL_DELAYED_QUERY");
        when(channelQueryService.query(any())).thenAnswer(invocation -> {
            queryEntered.countDown();
            assertTrue(releaseQuery.await(10, TimeUnit.SECONDS));
            return channelResult;
        });
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("paymentChannelQueryServiceImpl");
            registry.registerSingleton("paymentChannelQueryServiceImpl", channelQueryService);
            registry.removeBeanDefinition("paymentEventDispatchServiceImpl");
            registry.registerSingleton("paymentEventDispatchServiceImpl", dispatchService);
        }));
        String databaseUrl = "jdbc:h2:mem:payment_query_callback_race_http_" + System.nanoTime()
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
            String paymentOrderId = "PAY202607190002";
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<JsonNode> pendingQuery = executor.submit(() -> post(objectMapper, port,
                        "/api/payments/admin/query", "{\"paymentOrderId\":\"" + paymentOrderId
                                + "\",\"operator\":\"local-query-operator\"}", 200));
                assertTrue(queryEntered.await(10, TimeUnit.SECONDS));
                JsonNode callback = post(objectMapper, port, "/api/payments/callback/alipay_h5",
                        "{\"paymentOrderId\":\"" + paymentOrderId
                                + "\",\"channelTransactionNo\":\"ALI77665544\",\"tradeStatus\":\"SUCCESS\"}", 200);
                assertEquals("0", callback.path("code").asText());
                assertEquals("SUCCESS", callback.path("data").path("status").asText());
                releaseQuery.countDown();

                JsonNode query = pendingQuery.get(10, TimeUnit.SECONDS);
                if (conflicting) {
                    assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, query.path("code").asText());
                    assertTrue(query.path("data").isNull());
                    assertEquals(1, jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM t_payment_notify_log WHERE payment_order_id = ? "
                                    + "AND notify_type = 'QUERY_FAILED' AND notify_payload LIKE ? "
                                    + "AND notify_result LIKE ?",
                            Integer.class, paymentOrderId, "%local-query-operator%",
                            "%" + ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED + "%"));
                } else {
                    assertEquals("0", query.path("code").asText());
                    assertEquals("SUCCESS", query.path("data").path("status").asText());
                    assertEquals("LOCAL_DELAYED_QUERY", query.path("data").path("querySource").asText());
                    assertEquals("ALI77665544", query.path("data").path("channelTransactionNo").asText());
                }
                assertEquals("SUCCESS", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                        .path("data").path("status").asText());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                                + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, paymentOrderId));
                assertEquals("{\"channel\":\"alipay_h5\",\"payScene\":\"H5\"}", jdbcTemplate.queryForObject(
                        "SELECT event_payload FROM t_payment_event WHERE payment_order_id = ? "
                                + "AND event_type = 'PAYMENT_SUCCESS'", String.class, paymentOrderId));
                assertEquals("成功", jdbcTemplate.queryForObject(
                        "SELECT attempt_status FROM t_payment_attempt WHERE attempt_no = 'ATT202607190002'",
                        String.class));
                assertEquals("支付成功", jdbcTemplate.queryForObject(
                        "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = 'PRE202607190002'",
                        String.class));
                assertEquals("已结清", jdbcTemplate.queryForObject(
                        "SELECT bill_status FROM t_bill WHERE bill_no = 'BILL202607190002'",
                        String.class));
                verify(dispatchService, times(1)).publishPaymentSuccess(any(), eq(paymentOrderId));
            } finally {
                releaseQuery.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void shouldPersistAdminQueryFailureAuditWithoutChangingPaymentStatusOverHttp() throws Exception {
        PaymentChannelQueryService channelQueryService = mock(PaymentChannelQueryService.class);
        when(channelQueryService.query(any())).thenThrow(new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, "local query failure"));
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.addBeanFactoryPostProcessor(beanFactory -> {
            DefaultListableBeanFactory registry = (DefaultListableBeanFactory) beanFactory;
            registry.removeBeanDefinition("paymentChannelQueryServiceImpl");
            registry.registerSingleton("paymentChannelQueryServiceImpl", channelQueryService);
        }));
        String databaseUrl = "jdbc:h2:mem:payment_admin_query_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190002";
            assertEquals("WAIT_CALLBACK", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());

            JsonNode query = post(objectMapper, port, "/api/payments/admin/query",
                    "{\"paymentOrderId\":\"" + paymentOrderId
                            + "\",\"operator\":\"local-operator\"}", 200);
            assertEquals(ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED, query.path("code").asText());
            assertEquals("WAIT_CALLBACK", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());
            assertEquals("WAIT_CALLBACK", get(objectMapper, port,
                    "/api/payments?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("status").asText());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_notify_log WHERE payment_order_id = ? "
                            + "AND notify_type = 'QUERY_FAILED' AND notify_payload LIKE ? "
                            + "AND notify_result LIKE ?", Integer.class, paymentOrderId,
                    "%local-operator%", "%" + ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED + "%"));
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                            + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, paymentOrderId));
        }
    }

    @Test
    void shouldReturnSingleClosedPaymentAndEventAfterRepeatedAdminClose() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:payment_admin_close_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY-ADMIN-CLOSE-HTTP-001";
            String prepayOrderNo = "PRE-ADMIN-CLOSE-HTTP-001";
            assertEquals(1, jdbcTemplate.update(
                    "INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, amount, "
                            + "payment_method, channel_code, channel_transaction_no, status, status_type, "
                            + "success_at, created_at) SELECT ?, order_no, customer_name, amount, "
                            + "payment_method, channel_code, NULL, 'PREPAY_CREATED', 'info', NULL, created_at "
                            + "FROM t_payment_order WHERE payment_order_id = 'PAY202607190002'",
                    paymentOrderId));
            assertEquals(1, jdbcTemplate.update(
                    "INSERT INTO t_prepay_order (prepay_order_no, bill_no, order_no, customer_name, amount, "
                            + "pay_scene, cashier_title, cashier_status, cashier_status_type, payment_order_id, "
                            + "source_kind, created_at, expires_at) SELECT ?, bill_no, order_no, customer_name, "
                            + "amount, pay_scene, cashier_title, '待支付', 'warn', ?, source_kind, "
                            + "created_at, expires_at FROM t_prepay_order "
                            + "WHERE prepay_order_no = 'PRE202607190002'",
                    prepayOrderNo, paymentOrderId));
            assertEquals("PREPAY_CREATED", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());

            String body = "{\"paymentOrderId\":\"" + paymentOrderId
                    + "\",\"operator\":\"local-operator\"}";
            JsonNode first = post(objectMapper, port, "/api/payments/admin/close", body, 200);
            assertEquals("0", first.path("code").asText());
            assertEquals("CLOSED", first.path("data").path("status").asText());
            JsonNode replay = post(objectMapper, port, "/api/payments/admin/close", body, 200);
            assertEquals("0", replay.path("code").asText());
            assertEquals("CLOSED", replay.path("data").path("status").asText());
            assertEquals("CLOSED", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("data").path("status").asText());
            assertEquals("CLOSED", get(objectMapper, port,
                    "/api/payments?paymentOrderId=" + paymentOrderId)
                    .path("data").path("items").get(0).path("status").asText());
            assertEquals("已关闭", jdbcTemplate.queryForObject(
                    "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = ?",
                    String.class, prepayOrderNo));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                            + "AND event_type = 'PAYMENT_CLOSED'", Integer.class, paymentOrderId));
        }
    }

    @Test
    void shouldKeepAmbiguousPaymentListReadableButRejectDetailOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:payment_binding_http_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            String paymentOrderId = "PAY202607190002";
            assertEquals("0", get(objectMapper, port, "/api/payments/" + paymentOrderId)
                    .path("code").asText());

            assertEquals(1, jdbcTemplate.update(
                    "INSERT INTO t_prepay_order (prepay_order_no, bill_no, order_no, customer_name, amount, "
                            + "pay_scene, cashier_title, cashier_status, cashier_status_type, payment_order_id, "
                            + "source_kind, created_at, expires_at) "
                            + "SELECT 'PRE-DUPLICATE-002', bill_no, order_no, customer_name, amount, "
                            + "'HOME_REPAIR', cashier_title, cashier_status, cashier_status_type, payment_order_id, "
                            + "source_kind, TIMESTAMPADD(MINUTE, 1, created_at), expires_at "
                            + "FROM t_prepay_order WHERE prepay_order_no = 'PRE202607190002'"));
            assertPaymentBindingIssue(objectMapper, jdbcTemplate, port, paymentOrderId,
                    "MULTIPLE_PREPAY_BINDINGS");

            assertEquals(1, jdbcTemplate.update("DELETE FROM t_prepay_order "
                    + "WHERE prepay_order_no = 'PRE-DUPLICATE-002'"));
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET payment_order_id = 'pay202607190002' "
                    + "WHERE prepay_order_no = 'PRE202607190002'"));
            assertPaymentBindingIssue(objectMapper, jdbcTemplate, port, paymentOrderId,
                    "PREPAY_BINDING_MISSING");
        }
    }

    private void assertPaymentBindingIssue(ObjectMapper objectMapper, JdbcTemplate jdbcTemplate, int port,
                                           String paymentOrderId, String expectedIssue) throws Exception {
        JsonNode page = get(objectMapper, port, "/api/payments?paymentOrderId=" + paymentOrderId);
        assertEquals("0", page.path("code").asText());
        assertEquals(1, page.path("data").path("total").asInt());
        JsonNode row = page.path("data").path("items").get(0);
        assertTrue(row.path("payScene").isNull());
        assertEquals(expectedIssue, row.path("prepayBindingIssue").asText());
        JsonNode detail = get(objectMapper, port, "/api/payments/" + paymentOrderId);
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING, detail.path("code").asText());
        assertTrue(detail.path("data").isNull());
        String body = "{\"paymentOrderId\":\"" + paymentOrderId + "\"}";
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                post(objectMapper, port, "/api/payments/query", body, 200).path("code").asText());
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                post(objectMapper, port, "/api/payments/close", body, 200).path("code").asText());
        assertEquals(ErrorCode.PAYMENT_ORDER_SOURCE_MISSING,
                post(objectMapper, port, "/api/payments/callback/alipay_h5",
                        "{\"paymentOrderId\":\"" + paymentOrderId + "\",\"tradeStatus\":\"SUCCESS\","
                                + "\"channelTransactionNo\":\"ALI77665544\"}", 503)
                        .path("code").asText());
        assertEquals("WAIT_CALLBACK", jdbcTemplate.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                        + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, paymentOrderId));
        assertTrue(getCsv(port, "/api/payments/export?paymentOrderId=" + paymentOrderId)
                .endsWith(",\"" + expectedIssue + "\"\n"));
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String path, String body,
                          int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
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
        connection.setInstanceFollowRedirects(false);
        try {
            assertEquals(200, connection.getResponseCode());
            try (InputStream response = connection.getInputStream()) {
                return objectMapper.readTree(response);
            }
        } finally {
            connection.disconnect();
        }
    }

    private String getCsv(int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        try {
            assertEquals(200, connection.getResponseCode());
            assertTrue(connection.getContentType().startsWith("text/csv"));
            try (InputStream response = connection.getInputStream();
                 ByteArrayOutputStream content = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = response.read(buffer)) != -1) {
                    content.write(buffer, 0, bytesRead);
                }
                return new String(content.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            connection.disconnect();
        }
    }
}
