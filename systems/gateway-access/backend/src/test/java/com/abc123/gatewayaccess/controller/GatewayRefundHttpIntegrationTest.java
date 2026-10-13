package com.abc123.gatewayaccess.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.gatewayaccess.GatewayAccessSystemApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;

class GatewayRefundHttpIntegrationTest {

    @Test
    void shouldEnforceGovernanceAndForwardRefundToConfiguredLoopbackUpstream() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicReference<JsonNode> upstreamRequest = new AtomicReference<>();
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/refund/submit", exchange -> {
            upstreamCalls.incrementAndGet();
            upstreamRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = ("{\"code\":\"0\",\"data\":{"
                    + "\"refundOrderId\":\"REF-HTTP-001\",\"refundAttemptNo\":\"RFA-HTTP-001\","
                    + "\"channelRefundNo\":\"LOCAL-UPSTREAM-REF-001\","
                    + "\"status\":\"PROCESSING\",\"statusType\":\"warn\"}}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        upstream.start();
        try {
            String databaseUrl = "jdbc:h2:mem:gateway_refund_http_" + System.nanoTime()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
            SpringApplication application = new SpringApplication(GatewayAccessSystemApplication.class);
            try (ConfigurableApplicationContext context = application.run(
                    "--spring.datasource.url=" + databaseUrl,
                    "--server.address=127.0.0.1",
                    "--server.port=0",
                    "--gateway.transaction.access-key=local-test-key",
                    "--gateway.transaction.channels.wx_h5.refund-submit-url=http://127.0.0.1:"
                            + upstream.getAddress().getPort() + "/refund/submit")) {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                String request = "{\"refundOrderId\":\"REF-HTTP-001\","
                        + "\"refundAttemptNo\":\"RFA-HTTP-001\",\"paymentOrderId\":\"PAY-HTTP-001\","
                        + "\"orderNo\":\"ORD-HTTP-001\",\"customerName\":\"Local Customer\","
                        + "\"refundAmount\":20.00,\"refundMethod\":\"ORIGINAL_ROUTE\","
                        + "\"refundReason\":\"local integration\",\"channelCode\":\"wx_h5\"}";
                assertEquals("GATEWAY_ACCESS_DENIED",
                        post(objectMapper, port, request, "wrong-key").path("code").asText());
                assertEquals(0, upstreamCalls.get());

                assertEquals("GATEWAY_CERTIFICATE_INVALID",
                        post(objectMapper, port, request, "local-test-key").path("code").asText());
                assertEquals(0, upstreamCalls.get());
                assertEquals(1, jdbcTemplate.update("UPDATE t_gateway_certificate "
                        + "SET expire_at = '2030-01-01' WHERE certificate_code = 'CERT_WX_2026'"));

                JsonNode accepted = post(objectMapper, port, request, "local-test-key");
                assertEquals("0", accepted.path("code").asText());
                assertEquals("LOCAL-UPSTREAM-REF-001", accepted.path("data").path("channelRefundNo").asText());
                assertEquals("PROCESSING", accepted.path("data").path("status").asText());
                assertEquals(1, upstreamCalls.get());
                assertEquals("RFA-HTTP-001", upstreamRequest.get().path("refundAttemptNo").asText());
                assertEquals("PAY-HTTP-001", upstreamRequest.get().path("paymentOrderId").asText());
                assertEquals("wx_h5", upstreamRequest.get().path("channelCode").asText());
                assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_gateway_audit_log WHERE operation_type = 'REFUND_SUBMIT' "
                                + "AND result_status = 'SUCCESS' AND client_ip = '127.0.0.1'",
                        Integer.class));
                assertEquals(Integer.valueOf(2), jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_gateway_audit_log WHERE operation_type = 'REFUND_SUBMIT' "
                                + "AND result_status = 'FAILED' AND client_ip = '127.0.0.1' "
                                + "AND risk_hint IN ('GATEWAY_ACCESS_DENIED', 'GATEWAY_CERTIFICATE_INVALID')",
                        Integer.class));
            }
        } finally {
            upstream.stop(0);
        }
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String body, String accessKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + "/api/gateway/refunds/submit").openConnection();
        connection.setRequestMethod("POST");
        connection.setDoOutput(true);
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
        connection.setRequestProperty("X-Gateway-App-Code", "APP_PAY_CORE");
        connection.setRequestProperty("X-Gateway-Access-Key", accessKey);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(200, connection.getResponseCode());
            try (InputStream response = connection.getInputStream()) {
                return objectMapper.readTree(response);
            }
        } finally {
            connection.disconnect();
        }
    }
}
