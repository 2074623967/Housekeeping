package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentIssueActionRequestDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class PaymentIssueBatchActionHttpIntegrationTest {

    @Test
    void shouldReplayCommittedBatchResponseOverHttpWithoutRepeatingAction() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=jdbc:h2:mem:payment_issue_action_http_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            for (String paymentOrderId : Arrays.asList("PAY-ISSUE-HTTP-01", "PAY-ISSUE-HTTP-02")) {
                jdbc.update("INSERT INTO t_payment_order (payment_order_id, order_no, customer_name, "
                                + "amount, payment_method, channel_code, status, status_type, created_at) "
                                + "VALUES (?, ?, '本地用户', 10.00, '支付宝', 'alipay_h5', "
                                + "'WAIT_CALLBACK', 'warn', NOW())",
                        paymentOrderId, "ORD-" + paymentOrderId);
            }

            PaymentIssueActionRequestDTO request = new PaymentIssueActionRequestDTO();
            request.setIssueNos(Arrays.asList("ISSUE-WAIT-PAY-ISSUE-HTTP-01", "ISSUE-WAIT-PAY-ISSUE-HTTP-02"));
            request.setIdempotencyKey("issue-action-http-attempt-001");
            request.setActionType("分派处理人");
            request.setAssignee("后端值班");
            request.setOperator("支付运营");
            request.setRemark("核对渠道回调");
            JsonNode first = post(objectMapper, port, request);
            assertEquals("0", first.path("code").asText());
            JsonNode firstItems = first.path("data").path("items");
            assertEquals(2, firstItems.size());
            assertEquals("ISSUE-WAIT-PAY-ISSUE-HTTP-01", firstItems.get(0).path("issueNo").asText());
            assertEquals("本*", firstItems.get(0).path("customerName").asText());
            assertEquals("已分派", firstItems.get(0).path("handlingStatus").asText());
            assertEquals(2, actionCount(jdbc));

            jdbc.update("DELETE FROM t_payment_order WHERE payment_order_id = ?", "PAY-ISSUE-HTTP-01");
            request.setIssueNos(Arrays.asList("ISSUE-WAIT-PAY-ISSUE-HTTP-02", "ISSUE-WAIT-PAY-ISSUE-HTTP-01"));
            JsonNode replay = post(objectMapper, port, request);
            assertEquals("0", replay.path("code").asText());
            assertEquals(first.path("data"), replay.path("data"));
            assertNotEquals(first.path("requestId").asText(), replay.path("requestId").asText());
            assertEquals(2, actionCount(jdbc));

            request.setRemark("不同动作内容");
            JsonNode conflict = post(objectMapper, port, request);
            assertEquals(ErrorCode.PAYMENT_ISSUE_ACTION_IDEMPOTENCY_CONFLICT, conflict.path("code").asText());
            assertTrue(conflict.path("data").isNull());
            assertEquals(2, actionCount(jdbc));

            request.setIdempotencyKey(" ");
            assertEquals(ErrorCode.VALIDATION_ERROR, post(objectMapper, port, request).path("code").asText());
            assertEquals(2, actionCount(jdbc));

            request.setIssueNos(Arrays.asList("ISSUE-WAIT-PAY-ISSUE-HTTP-02"));
            request.setRemark("大小写独立");
            request.setIdempotencyKey("Case-Key-HTTP");
            assertEquals("0", post(objectMapper, port, request).path("code").asText());
            request.setIdempotencyKey("case-key-http");
            assertEquals("0", post(objectMapper, port, request).path("code").asText());
            assertEquals(4, actionCount(jdbc));
            assertEquals(3, jdbc.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_issue_action_request", Integer.class));
        }
    }

    private int actionCount(JdbcTemplate jdbc) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM t_payment_issue_action_log "
                + "WHERE issue_no LIKE 'ISSUE-WAIT-PAY-ISSUE-HTTP-%'", Integer.class);
    }

    private JsonNode post(ObjectMapper objectMapper, int port, PaymentIssueActionRequestDTO request)
            throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + "/api/payment-issues/actions").openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
        connection.setDoOutput(true);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(objectMapper.writeValueAsBytes(request));
        }
        try (InputStream input = connection.getInputStream()) {
            assertEquals(200, connection.getResponseCode());
            return objectMapper.readTree(input);
        } finally {
            connection.disconnect();
        }
    }
}
