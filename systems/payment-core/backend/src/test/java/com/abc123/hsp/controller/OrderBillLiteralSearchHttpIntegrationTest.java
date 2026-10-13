package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class OrderBillLiteralSearchHttpIntegrationTest {

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
    void shouldFindStandardOrderAndBillByLiteralFiltersOverHttp() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        String databaseUrl = "jdbc:h2:mem:order_bill_literal_http_" + System.nanoTime()
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
            String orderNo = "ORDER-HTTP-%_!\\\"";
            String billNo = "BILL-HTTP-%_!\\\"";
            String customerName = "客户%_!\\\"";
            jdbcTemplate.update("INSERT INTO t_order (order_no, customer_name, service_type, worker_name, "
                            + "order_amount, paid_amount, order_status, order_status_type, "
                            + "fulfillment_status, fulfillment_status_type, created_at) "
                            + "VALUES (?, ?, '深度保洁', '本地服务者', 88.00, 0.00, '待支付', 'warn', "
                            + "'待履约', 'info', CURRENT_TIMESTAMP)", orderNo, customerName);
            jdbcTemplate.update("INSERT INTO t_bill (bill_no, order_no, customer_name, bill_amount, "
                            + "paid_amount, bill_status, bill_status_type, due_at, created_at) "
                            + "VALUES (?, ?, ?, 88.00, 0.00, '待支付', 'warn', "
                            + "TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), CURRENT_TIMESTAMP)",
                    billNo, orderNo, customerName);
            jdbcTemplate.update("INSERT INTO t_order (order_no, customer_name, service_type, worker_name, "
                            + "order_amount, paid_amount, order_status, order_status_type, "
                            + "fulfillment_status, fulfillment_status_type, created_at) "
                            + "VALUES ('ORDER-HTTP-LEGACY', '本地客户', '深度保洁', '本地服务者', "
                            + "88.00, 0.00, '待支付', 'warn', '待履约', 'info', CURRENT_TIMESTAMP)");
            jdbcTemplate.update("INSERT INTO t_bill (bill_no, order_no, customer_name, bill_amount, "
                            + "paid_amount, bill_status, bill_status_type, due_at, created_at) "
                            + "VALUES ('BILL-HTTP-LEGACY', 'ORDER-HTTP-LEGACY', '本地客户', "
                            + "88.00, 0.00, '待支付', 'warn', TIMESTAMPADD(DAY, 1, CURRENT_TIMESTAMP), "
                            + "CURRENT_TIMESTAMP)");

            for (String fragment : Arrays.asList("%", "_", "!", "\\", "%_!\\\"")) {
                String filter = "orderNo=" + encode(fragment);
                JsonNode orders = get(objectMapper, port, "/api/orders?" + filter).path("data");
                assertEquals(1, orders.path("total").asInt());
                assertEquals(orderNo, orders.path("items").get(0).path("orderNo").asText());

                for (String field : Arrays.asList("billNo", "orderNo", "customerName")) {
                    String billFilter = field + "=" + encode(fragment);
                    JsonNode bills = get(objectMapper, port, "/api/bills?" + billFilter).path("data");
                    assertEquals(1, bills.path("total").asInt());
                    assertEquals(billNo, bills.path("items").get(0).path("billNo").asText());
                    JsonNode overview = get(objectMapper, port, "/api/bills/overview?" + billFilter)
                            .path("data");
                    assertEquals(1, overview.path("totalBillCount").asInt());
                }
            }

            String combined = "billNo=" + encode(billNo) + "&orderNo=" + encode(orderNo)
                    + "&customerName=" + encode(customerName);
            assertEquals(1, get(objectMapper, port, "/api/bills?" + combined)
                    .path("data").path("total").asInt());
            assertEquals(orderNo, get(objectMapper, port, "/api/orders/by-no?orderNo=" + encode(orderNo))
                    .path("data").path("orderNo").asText());
            assertEquals(billNo, get(objectMapper, port, "/api/bills/by-no?billNo=" + encode(billNo))
                    .path("data").path("billNo").asText());
            assertEquals("ORDER-HTTP-LEGACY", get(objectMapper, port, "/api/orders/ORDER-HTTP-LEGACY")
                    .path("data").path("orderNo").asText());
            assertEquals("BILL-HTTP-LEGACY", get(objectMapper, port, "/api/bills/BILL-HTTP-LEGACY")
                    .path("data").path("billNo").asText());
            assertEquals(2, getCsv(port, "/api/orders/export?orderNo=" + encode("%"))
                    .trim().split("\\R").length);
            assertEquals(2, getCsv(port, "/api/bills/export?" + combined)
                    .trim().split("\\R").length);
        }
    }

    private String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private JsonNode get(ObjectMapper objectMapper, int port, String path) throws Exception {
        HttpURLConnection connection = open(port, path);
        try (InputStream response = connection.getInputStream()) {
            return objectMapper.readTree(response);
        } finally {
            connection.disconnect();
        }
    }

    private String getCsv(int port, String path) throws Exception {
        HttpURLConnection connection = open(port, path);
        try (InputStream response = connection.getInputStream();
             ByteArrayOutputStream content = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = response.read(buffer)) != -1) {
                content.write(buffer, 0, bytesRead);
            }
            return new String(content.toByteArray(), StandardCharsets.UTF_8);
        } finally {
            connection.disconnect();
        }
    }

    private HttpURLConnection open(int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path)
                .openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        connection.setInstanceFollowRedirects(false);
        assertEquals(200, connection.getResponseCode(), path);
        return connection;
    }
}
