package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentCallbackTransportHttpIntegrationTest {

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
    void shouldNotAcknowledgeRejectedCallbacksWhilePreservingOtherApiErrors() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:payment_callback_transport_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1",
                "--server.port=0",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            assertEquals(ErrorCode.VALIDATION_ERROR, post(objectMapper, port,
                    "/api/payments/callback/wx_h5", "{", 503).path("code").asText());
            assertEquals(ErrorCode.VALIDATION_ERROR, post(objectMapper, port,
                    "/api/payments/callback/wx_h5", "{}", 503).path("code").asText());
            assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, post(objectMapper, port,
                    "/api/payments/query", "{}", 200).path("code").asText());

            assertEquals("SUCCESS", post(objectMapper, port, "/api/payments/callback/offline_bank",
                    "{\"paymentOrderId\":\"PAY202607180074\",\"tradeStatus\":\"SUCCESS\","
                            + "\"channelTransactionNo\":\"BANK332211\"}", 200)
                    .path("data").path("status").asText());

            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET status='CLOSED', "
                    + "status_type='danger' WHERE payment_order_id='PAY202607190002'"));
            assertEquals(ErrorCode.SYSTEM_ERROR, post(objectMapper, port,
                    "/api/payments/callback/alipay_h5",
                    "{\"paymentOrderId\":\"PAY202607190002\",\"tradeStatus\":\"SUCCESS\","
                            + "\"channelTransactionNo\":\"ALI77665544\"}", 503)
                    .path("code").asText());
            assertEquals("CLOSED", jdbcTemplate.queryForObject(
                    "SELECT status FROM t_payment_order WHERE payment_order_id='PAY202607190002'",
                    String.class));
            assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id='PAY202607190002' "
                            + "AND event_type='PAYMENT_SUCCESS'", Integer.class));
        }
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String path, String body,
            int expectedStatus) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(3000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(expectedStatus, connection.getResponseCode());
            try (InputStream input = expectedStatus >= 400
                    ? connection.getErrorStream() : connection.getInputStream()) {
                return objectMapper.readTree(input);
            }
        } finally {
            connection.disconnect();
        }
    }
}
