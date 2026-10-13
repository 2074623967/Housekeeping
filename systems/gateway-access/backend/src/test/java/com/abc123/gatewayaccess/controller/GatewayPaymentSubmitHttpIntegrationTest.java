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

class GatewayPaymentSubmitHttpIntegrationTest {

    @Test
    void shouldRejectMismatchedSubmitReceiptAfterUpstreamInvocation() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        AtomicInteger upstreamCalls = new AtomicInteger();
        AtomicReference<JsonNode> upstreamRequest = new AtomicReference<>();
        AtomicReference<String> upstreamResponse = new AtomicReference<>(
                "{\"code\":\"0\",\"data\":{\"paymentOrderId\":\"PAY-HTTP-001\","
                        + "\"prepayOrderNo\":\"PRE-HTTP-001\",\"orderNo\":\"ORD-HTTP-001\","
                        + "\"idempotencyKey\":\"local-idem-001\",\"channelTransactionNo\":\"WX-HTTP-001\","
                        + "\"attemptStatus\":\"处理中\",\"attemptStatusType\":\"info\","
                        + "\"channelReturnCode\":\"USERPAYING\"}}");
        HttpServer upstream = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        upstream.createContext("/payment/submit", exchange -> {
            upstreamCalls.incrementAndGet();
            upstreamRequest.set(objectMapper.readTree(exchange.getRequestBody()));
            byte[] response = upstreamResponse.get().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(response);
            }
        });
        upstream.start();
        try {
            String databaseUrl = "jdbc:h2:mem:gateway_payment_submit_http_" + System.nanoTime()
                    + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE";
            SpringApplication application = new SpringApplication(GatewayAccessSystemApplication.class);
            try (ConfigurableApplicationContext context = application.run(
                    "--spring.datasource.url=" + databaseUrl,
                    "--server.address=127.0.0.1",
                    "--server.port=0",
                    "--gateway.transaction.access-key=local-test-key",
                    "--gateway.transaction.channels.wx_h5.payment-submit-url=http://127.0.0.1:"
                            + upstream.getAddress().getPort() + "/payment/submit")) {
                int port = ((WebServerApplicationContext) context).getWebServer().getPort();
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                String request = "{\"paymentOrderId\":\"PAY-HTTP-001\","
                        + "\"prepayOrderNo\":\"PRE-HTTP-001\",\"orderNo\":\"ORD-HTTP-001\","
                        + "\"payScene\":\"HOME_SERVICE\",\"customerName\":\"Local Customer\","
                        + "\"amount\":20.00,\"paymentMethod\":\"wechat\",\"sourceAppId\":\"APP-H5\","
                        + "\"requestedChannelCode\":\"wx_h5\",\"resolvedChannelCode\":\"wx_h5\","
                        + "\"terminal\":\"H5\",\"clientIp\":\"127.0.0.1\","
                        + "\"idempotencyKey\":\"local-idem-001\"}";

                assertEquals("GATEWAY_ACCESS_DENIED",
                        post(objectMapper, port, request, "wrong-key").path("code").asText());
                assertEquals("GATEWAY_CERTIFICATE_INVALID",
                        post(objectMapper, port, request, "local-test-key").path("code").asText());
                assertEquals(0, upstreamCalls.get());
                assertEquals(1, jdbcTemplate.update("UPDATE t_gateway_certificate "
                        + "SET expire_at = '2030-01-01' WHERE certificate_code = 'CERT_WX_2026'"));

                JsonNode accepted = post(objectMapper, port, request, "local-test-key");
                assertEquals("0", accepted.path("code").asText());
                assertEquals("PAY-HTTP-001", accepted.path("data").path("paymentOrderId").asText());
                assertEquals("local-idem-001", accepted.path("data").path("idempotencyKey").asText());
                assertEquals("WX-HTTP-001", accepted.path("data").path("channelTransactionNo").asText());
                assertEquals("local-idem-001", upstreamRequest.get().path("idempotencyKey").asText());
                assertEquals(1, upstreamCalls.get());

                upstreamResponse.set(upstreamResponse.get().replace("local-idem-001", "local-idem-other"));
                assertEquals("GATEWAY_UPSTREAM_INVALID_RESPONSE",
                        post(objectMapper, port, request, "local-test-key").path("code").asText());
                assertEquals(2, upstreamCalls.get());
                assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_gateway_audit_log WHERE operation_type = 'PAYMENT_SUBMIT' "
                                + "AND result_status = 'SUCCESS' AND client_ip = '127.0.0.1'", Integer.class));
                assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_gateway_audit_log WHERE operation_type = 'PAYMENT_SUBMIT' "
                                + "AND result_status = 'UNKNOWN' AND client_ip = '127.0.0.1'", Integer.class));
            }
        } finally {
            upstream.stop(0);
        }
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String body, String accessKey) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + "/api/gateway/payment-orders/submit").openConnection();
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
