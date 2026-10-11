package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentChannelSubmitAdapter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.web.context.WebServerApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.FileSystemResource;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentSubmitReplayIntegrationTest {

    private static final String LONG_CHANNEL_CODE = "LONG-" + String.join("", java.util.Collections.nCopies(59, "C"));

    @Order(5)
    private static class BlockingChannelSubmitAdapter implements PaymentChannelSubmitAdapter {
        private final String channelCode;
        private final CountDownLatch entered;
        private final CountDownLatch release;
        private final AtomicInteger calls;

        BlockingChannelSubmitAdapter(String channelCode, CountDownLatch entered,
                CountDownLatch release, AtomicInteger calls) {
            this.channelCode = channelCode;
            this.entered = entered;
            this.release = release;
            this.calls = calls;
        }

        @Override
        public boolean supports(String candidate) {
            return channelCode.equals(candidate);
        }

        @Override
        public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
            calls.incrementAndGet();
            entered.countDown();
            try {
                if (!release.await(15, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("channel submit timed out");
                }
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("channel submit interrupted", exception);
            }
            PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
            result.setChannelTransactionNo("BLOCK-WX-001");
            result.setChannelReturnCode("USERPAYING");
            result.setAttemptStatus("处理中");
            result.setAttemptStatusType("info");
            result.setResponsePayload("{\"accepted\":true}");
            return result;
        }
    }

    private static PaymentChannelSubmitAdapter longChannelSubmitAdapter() {
        return new PaymentChannelSubmitAdapter() {
            @Override
            public boolean supports(String channelCode) {
                return LONG_CHANNEL_CODE.equals(channelCode);
            }

            @Override
            public PaymentChannelSubmitResultDTO submit(PaymentChannelSubmitRequestDTO request) {
                PaymentChannelSubmitResultDTO result = new PaymentChannelSubmitResultDTO();
                result.setChannelTransactionNo("LONG-CHANNEL-001");
                result.setChannelReturnCode("USERPAYING");
                result.setAttemptStatus("处理中");
                result.setAttemptStatusType("info");
                result.setResponsePayload("{\"accepted\":true}");
                return result;
            }
        };
    }

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
    void shouldPersistAndReplayGeneratedKeyForLongRoutedChannel() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.addInitializers(context -> context.getBeanFactory().registerSingleton(
                "longChannelSubmitAdapter", longChannelSubmitAdapter()));
        String databaseUrl = "jdbc:h2:mem:payment_submit_long_auto_key_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
            prepayRequest.setOrderNo("DEMO-LONG-AUTO-KEY");
            prepayRequest.setPayScene("HOME_CLEAN");
            prepayRequest.setCustomerName("demo long auto key");
            prepayRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);
            String longPrepayOrderNo = "PRE-" + String.join("", java.util.Collections.nCopies(60, "P"));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_prepay_order SET prepay_order_no = ? WHERE prepay_order_no = ?",
                    longPrepayOrderNo, prepay.getPrepayOrderNo()));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_channel_config SET channel_code = ? WHERE channel_code = 'wx_h5'",
                    LONG_CHANNEL_CODE));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_control_policy SET allowed_channel_codes = ? WHERE source_app_id = ?",
                    "wx_h5,alipay_h5," + LONG_CHANNEL_CODE, "housekeeping-h5-web"));

            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo(longPrepayOrderNo);
            request.setPaymentMethod("微信支付");
            request.setChannelCode(LONG_CHANNEL_CODE);
            request.setSourceAppId("housekeeping-h5-web");
            request.setMerchantNo("MCH_WX_10001");
            request.setAccessToken("token-housekeeping-h5-web");
            request.setTerminal("H5");
            request.setClientIp("127.0.0.1");
            request.setPayerPhone("13800000000");
            assertEquals(prepay.getPaymentOrderId(), paymentService.submit(request).getPaymentOrderId());
            String storedKey = jdbcTemplate.queryForObject(
                    "SELECT idempotency_key FROM t_payment_attempt WHERE payment_order_id = ?",
                    String.class, prepay.getPaymentOrderId());
            assertTrue(storedKey.matches("AUTO:[0-9a-f]{64}"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE idempotency_key = ?",
                    Integer.class, storedKey));
            assertEquals(prepay.getPaymentOrderId(), paymentService.submit(request).getPaymentOrderId());
            request.setPayerPhone("13800000001");
            BusinessException conflict = assertThrows(BusinessException.class, () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, conflict.getCode());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, prepay.getPaymentOrderId()));
        }
    }

    @Test
    void shouldNotTakeOverExpiredActiveSubmitTokenBeforeManualFactReview() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_token_expired_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
            prepayRequest.setOrderNo("DEMO-EXPIRED-ACTIVE-TOKEN");
            prepayRequest.setPayScene("HOME_CLEAN");
            prepayRequest.setCustomerName("demo expired active token");
            prepayRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);
            String prepayOrderNo = prepay.getPrepayOrderNo();
            String paymentOrderId = prepay.getPaymentOrderId();
            assertEquals(1, jdbcTemplate.update(
                    "INSERT INTO t_payment_submit_concurrency_token (prepay_order_no, payment_order_id, "
                            + "source_app_id, token_status, holder_idempotency_key, holder_terminal, holder_client_ip, "
                            + "last_occupied_at, expire_at, created_at, updated_at) VALUES "
                            + "(?, ?, 'housekeeping-h5-web', ' active ', 'IDEMP-OLD-HOLDER', 'H5', "
                            + "'127.0.0.1', NOW(), TIMESTAMPADD(MINUTE, 1, NOW()), NOW(), NOW())",
                    prepayOrderNo, paymentOrderId));

            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo(prepayOrderNo);
            request.setPaymentMethod("微信支付");
            request.setChannelCode("wx_h5");
            request.setSourceAppId("housekeeping-h5-web");
            request.setMerchantNo("MCH_WX_10001");
            request.setAccessToken("token-housekeeping-h5-web");
            request.setTerminal("H5");
            request.setClientIp("127.0.0.1");
            request.setIdempotencyKey("IDEMP-OLD-HOLDER");
            BusinessException sameKeyBlocked = assertThrows(BusinessException.class,
                    () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_CONCURRENCY_BLOCKED, sameKeyBlocked.getCode());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_submit_concurrency_token "
                    + "SET expire_at = TIMESTAMPADD(MINUTE, -1, NOW()) WHERE prepay_order_no = ?",
                    prepayOrderNo));
            BusinessException expiredSameKeyBlocked = assertThrows(BusinessException.class,
                    () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_CONCURRENCY_BLOCKED, expiredSameKeyBlocked.getCode());
            request.setIdempotencyKey("IDEMP-NEW-HOLDER");

            BusinessException blocked = assertThrows(BusinessException.class, () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_CONCURRENCY_BLOCKED, blocked.getCode());
            assertEquals(0, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals("IDEMP-OLD-HOLDER", jdbcTemplate.queryForObject(
                    "SELECT holder_idempotency_key FROM t_payment_submit_concurrency_token "
                            + "WHERE prepay_order_no = ?", String.class, prepayOrderNo));

            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_submit_concurrency_token "
                    + "SET token_status = 'RELEASED' WHERE prepay_order_no = ?", prepayOrderNo));
            assertEquals(paymentOrderId, paymentService.submit(request).getPaymentOrderId());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals("IDEMP-NEW-HOLDER", jdbcTemplate.queryForObject(
                    "SELECT holder_idempotency_key FROM t_payment_submit_concurrency_token "
                            + "WHERE prepay_order_no = ?", String.class, prepayOrderNo));
        }
    }

    @Test
    void shouldRejectSamePaymentKeyWithChangedSubmitContent() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_content_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
            prepayRequest.setOrderNo("DEMO-IDEMPOTENCY-CONTENT-001");
            prepayRequest.setPayScene("HOME_CLEAN");
            prepayRequest.setCustomerName("demo idempotency");
            prepayRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);

            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo(prepay.getPrepayOrderNo());
            request.setPaymentMethod("微信支付");
            request.setChannelCode("wx_h5");
            request.setSourceAppId("housekeeping-h5-web");
            request.setMerchantNo("MCH_WX_10001");
            request.setAccessToken("token-housekeeping-h5-web");
            request.setTerminal("H5");
            request.setClientIp("127.0.0.1");
            request.setPayerPhone("13800000000");
            request.setIdempotencyKey("DEMO-IDEMPOTENCY-CONTENT-001");
            paymentService.submit(request);
            String fingerprint = jdbcTemplate.queryForObject(
                    "SELECT request_fingerprint FROM t_payment_attempt WHERE idempotency_key = ?",
                    String.class, request.getIdempotencyKey());
            assertTrue(fingerprint.matches("[0-9a-f]{64}"));
            assertFalse(jdbcTemplate.queryForObject(
                    "SELECT request_payload FROM t_payment_attempt WHERE idempotency_key = ?",
                    String.class, request.getIdempotencyKey()).contains(request.getAccessToken()));
            request.setClientIp("127.0.0.2");
            assertEquals(prepay.getPaymentOrderId(), paymentService.submit(request).getPaymentOrderId());

            request.setPayerPhone("13800000001");
            BusinessException conflict = assertThrows(BusinessException.class,
                    () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, conflict.getCode());
            request.setPayerPhone("13800000000");
            request.setClientDeviceId("new-device");
            assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                    assertThrows(BusinessException.class, () -> paymentService.submit(request)).getCode());
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, prepay.getPaymentOrderId()));
        }
    }

    @Test
    void shouldRejectDifferentContentWhenSameKeySubmitsConcurrently() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_content_race_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;LOCK_TIMEOUT=10000";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
            prepayRequest.setOrderNo("DEMO-IDEMPOTENCY-RACE-001");
            prepayRequest.setPayScene("HOME_CLEAN");
            prepayRequest.setCustomerName("demo idempotency race");
            prepayRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);
            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Object> first = executor.submit(() -> submitAfterStart(
                        paymentService, prepay.getPrepayOrderNo(), "DEMO-IDEMPOTENCY-RACE-001",
                        "13800000000", ready, start));
                Future<Object> second = executor.submit(() -> submitAfterStart(
                        paymentService, prepay.getPrepayOrderNo(), "DEMO-IDEMPOTENCY-RACE-001",
                        "13800000001", ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                List<Object> results = Arrays.asList(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertEquals(1, results.stream().filter(PrepayOrderDTO.class::isInstance).count());
                assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count());
                BusinessException conflict = (BusinessException) results.stream()
                        .filter(BusinessException.class::isInstance).findFirst().get();
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, conflict.getCode());
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                        Integer.class, prepay.getPaymentOrderId()));
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                        Integer.class, prepay.getPaymentOrderId()));
                assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                        "SELECT token_status FROM t_payment_submit_concurrency_token WHERE prepay_order_no = ?",
                        String.class, prepay.getPrepayOrderNo()));
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void shouldNotReplayUnacceptedChannelSubmitOverHttp() throws Exception {
        String channelCode = "blocking_wx_h5";
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        AtomicInteger calls = new AtomicInteger();
        PaymentChannelSubmitAdapter adapter = new BlockingChannelSubmitAdapter(channelCode, entered, release, calls);
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.addInitializers(context -> context.getBeanFactory().registerSingleton("blockingAdapter", adapter));
        String databaseUrl = "jdbc:h2:mem:payment_pending_replay_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;LOCK_TIMEOUT=10000";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo", "--spring.datasource.url=" + databaseUrl,
                "--server.address=127.0.0.1", "--server.port=0",
                "--payment.ops-config.enabled=false", "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            ObjectMapper objectMapper = context.getBean(ObjectMapper.class);
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_channel_config SET channel_code = ? WHERE channel_code = 'wx_h5'",
                    channelCode));
            assertEquals(1, jdbcTemplate.update(
                    "UPDATE t_payment_control_policy SET allowed_channel_codes = ? WHERE source_app_id = ?",
                    "wx_h5,alipay_h5," + channelCode, "housekeeping-h5-web"));
            PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
            prepayRequest.setOrderNo("DEMO-PENDING-REPLAY-001");
            prepayRequest.setPayScene("HOME_CLEAN");
            prepayRequest.setCustomerName("demo pending replay");
            prepayRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);
            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo(prepay.getPrepayOrderNo());
            request.setPaymentMethod("微信支付");
            request.setChannelCode(channelCode);
            request.setSourceAppId("housekeeping-h5-web");
            request.setMerchantNo("MCH_WX_10001");
            request.setAccessToken("token-housekeeping-h5-web");
            request.setTerminal("H5");
            request.setClientIp("127.0.0.1");
            request.setIdempotencyKey("DEMO-PENDING-REPLAY-001");
            String requestBody = objectMapper.writeValueAsString(request);
            ExecutorService executor = Executors.newSingleThreadExecutor();
            try {
                Future<JsonNode> first = executor.submit(() -> post(objectMapper, port, requestBody));
                if (!entered.await(10, TimeUnit.SECONDS)) {
                    first.get(1, TimeUnit.SECONDS);
                    throw new AssertionError("channel submit was not entered");
                }
                assertEquals("WAIT_CALLBACK", jdbcTemplate.queryForObject(
                        "SELECT status FROM t_payment_order WHERE payment_order_id = ?",
                        String.class, prepay.getPaymentOrderId()));
                assertNull(jdbcTemplate.queryForObject(
                        "SELECT channel_transaction_no FROM t_payment_order WHERE payment_order_id = ?",
                        String.class, prepay.getPaymentOrderId()));
                assertEquals("支付中", jdbcTemplate.queryForObject(
                        "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = ?",
                        String.class, prepay.getPrepayOrderNo()));
                JsonNode detail = get(objectMapper, port,
                        "/api/payments/" + prepay.getPaymentOrderId()).path("data");
                assertEquals("PAYING", detail.path("status").asText());
                assertEquals("info", detail.path("statusType").asText());
                JsonNode cashier = get(objectMapper, port,
                        "/api/payments/cashier/" + prepay.getPrepayOrderNo()).path("data");
                assertEquals("PAYING", cashier.path("paymentStatus").asText());
                assertEquals("支付中", cashier.path("status").asText());
                assertTrue(cashier.path("channelTransactionNo").isNull()
                        || cashier.path("channelTransactionNo").isMissingNode());
                JsonNode reusedPrepay = post(objectMapper, port, "/api/payments/prepay",
                        objectMapper.writeValueAsString(prepayRequest));
                assertEquals("0", reusedPrepay.path("code").asText());
                assertEquals(prepay.getPrepayOrderNo(), reusedPrepay.path("data").path("prepayOrderNo").asText());
                assertEquals("PAYING", reusedPrepay.path("data").path("paymentStatus").asText());
                assertEquals("支付中", reusedPrepay.path("data").path("cashierStatus").asText());
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, post(objectMapper, port,
                        "/api/payments/close", "{\"paymentOrderId\":\"" + prepay.getPaymentOrderId()
                                + "\"}").path("code").asText());
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS, post(objectMapper, port,
                        "/api/payments/query", "{\"paymentOrderId\":\"" + prepay.getPaymentOrderId()
                                + "\"}").path("code").asText());
                assertNull(jdbcTemplate.queryForObject(
                        "SELECT last_channel_return_code FROM t_payment_order WHERE payment_order_id = ?",
                        String.class, prepay.getPaymentOrderId()));
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IN_PROGRESS,
                        post(objectMapper, port, requestBody).path("code").asText());
                request.setPayerPhone("13800000001");
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                        post(objectMapper, port, objectMapper.writeValueAsString(request)).path("code").asText());
                request.setPayerPhone("13800000000");
                request.setIdempotencyKey("DEMO-PENDING-REPLAY-ALT-001");
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                        post(objectMapper, port, objectMapper.writeValueAsString(request)).path("code").asText());
                assertEquals(1, calls.get());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                        Integer.class, prepay.getPaymentOrderId()));
                assertEquals("ACTIVE", jdbcTemplate.queryForObject(
                        "SELECT token_status FROM t_payment_submit_concurrency_token WHERE prepay_order_no = ?",
                        String.class, prepay.getPrepayOrderNo()));
                release.countDown();
                assertEquals("WAIT_CALLBACK", first.get(15, TimeUnit.SECONDS)
                        .path("data").path("paymentStatus").asText());
                assertEquals("WAIT_CALLBACK", get(objectMapper, port,
                        "/api/payments/" + prepay.getPaymentOrderId())
                        .path("data").path("status").asText());
                assertEquals("WAIT_CALLBACK", get(objectMapper, port,
                        "/api/payments/cashier/" + prepay.getPrepayOrderNo())
                        .path("data").path("paymentStatus").asText());
                assertEquals("WAIT_CALLBACK", post(objectMapper, port, "/api/payments/prepay",
                        objectMapper.writeValueAsString(prepayRequest))
                        .path("data").path("paymentStatus").asText());
                assertEquals("WAIT_CALLBACK", post(objectMapper, port, requestBody)
                        .path("data").path("paymentStatus").asText());
                assertEquals("WAIT_CALLBACK", post(objectMapper, port,
                        objectMapper.writeValueAsString(request)).path("data").path("paymentStatus").asText());
                assertEquals(1, calls.get());
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                        Integer.class, prepay.getPaymentOrderId()));
            } finally {
                release.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void shouldRejectSameKeyWhenDifferentPrepayOrdersSubmitConcurrently() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_cross_prepay_race_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE;LOCK_TIMEOUT=10000";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            PrepayRequestDTO firstRequest = new PrepayRequestDTO();
            firstRequest.setOrderNo("DEMO-CROSS-PREPAY-RACE-A");
            firstRequest.setPayScene("HOME_CLEAN");
            firstRequest.setCustomerName("demo cross prepay A");
            firstRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO firstPrepay = paymentService.prepay(firstRequest);
            PrepayRequestDTO secondRequest = new PrepayRequestDTO();
            secondRequest.setOrderNo("DEMO-CROSS-PREPAY-RACE-B");
            secondRequest.setPayScene("HOME_CLEAN");
            secondRequest.setCustomerName("demo cross prepay B");
            secondRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO secondPrepay = paymentService.prepay(secondRequest);

            CountDownLatch ready = new CountDownLatch(2);
            CountDownLatch start = new CountDownLatch(1);
            ExecutorService executor = Executors.newFixedThreadPool(2);
            try {
                Future<Object> first = executor.submit(() -> submitAfterStart(
                        paymentService, firstPrepay.getPrepayOrderNo(), "DEMO-CROSS-PREPAY-SHARED",
                        "13800000000", ready, start));
                Future<Object> second = executor.submit(() -> submitAfterStart(
                        paymentService, secondPrepay.getPrepayOrderNo(), "DEMO-CROSS-PREPAY-SHARED",
                        "13800000000", ready, start));
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                List<Object> results = Arrays.asList(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
                assertEquals(1, results.stream().filter(PrepayOrderDTO.class::isInstance).count());
                assertEquals(1, results.stream().filter(BusinessException.class::isInstance).count());
                BusinessException conflict = (BusinessException) results.stream()
                        .filter(BusinessException.class::isInstance).findFirst().get();
                assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, conflict.getCode());
                JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
                assertEquals(1, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_attempt WHERE idempotency_key = ?",
                        Integer.class, "DEMO-CROSS-PREPAY-SHARED"));
                PrepayOrderDTO accepted = (PrepayOrderDTO) results.stream()
                        .filter(PrepayOrderDTO.class::isInstance).findFirst().get();
                String losingPrepayOrderNo = firstPrepay.getPrepayOrderNo().equals(accepted.getPrepayOrderNo())
                        ? secondPrepay.getPrepayOrderNo() : firstPrepay.getPrepayOrderNo();
                assertEquals("待支付", jdbcTemplate.queryForObject(
                        "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = ?",
                        String.class, losingPrepayOrderNo));
                assertEquals(0, jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM t_payment_submit_concurrency_token WHERE prepay_order_no = ?",
                        Integer.class, losingPrepayOrderNo));
            } finally {
                start.countDown();
                executor.shutdownNow();
            }
        }
    }

    @Test
    void shouldPersistDistinctKeysDespiteCaseInsensitiveRawCollation() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_collation_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            PaymentService paymentService = context.getBean(PaymentService.class);
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt ALTER COLUMN idempotency_key VARCHAR_IGNORECASE(128)");
            PrepayRequestDTO firstRequest = new PrepayRequestDTO();
            firstRequest.setOrderNo("DEMO-COLLATION-PREPAY-A");
            firstRequest.setPayScene("HOME_CLEAN");
            firstRequest.setCustomerName("demo collation A");
            firstRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO firstPrepay = paymentService.prepay(firstRequest);
            PrepayRequestDTO secondRequest = new PrepayRequestDTO();
            secondRequest.setOrderNo("DEMO-COLLATION-PREPAY-B");
            secondRequest.setPayScene("HOME_CLEAN");
            secondRequest.setCustomerName("demo collation B");
            secondRequest.setAmount(new BigDecimal("88.00"));
            PrepayOrderDTO secondPrepay = paymentService.prepay(secondRequest);
            String keyPrefix = String.join("", java.util.Collections.nCopies(86, "界"));

            PaymentSubmitRequestDTO submit = new PaymentSubmitRequestDTO();
            submit.setPrepayOrderNo(firstPrepay.getPrepayOrderNo());
            submit.setPaymentMethod("微信支付");
            submit.setChannelCode("wx_h5");
            submit.setSourceAppId("housekeeping-h5-web");
            submit.setMerchantNo("MCH_WX_10001");
            submit.setAccessToken("token-housekeeping-h5-web");
            submit.setTerminal("H5");
            submit.setIdempotencyKey(keyPrefix + "A");
            paymentService.submit(submit);

            submit.setPrepayOrderNo(secondPrepay.getPrepayOrderNo());
            submit.setIdempotencyKey(keyPrefix + "a");
            assertEquals(secondPrepay.getPaymentOrderId(), paymentService.submit(submit).getPaymentOrderId());
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE idempotency_key = ?",
                    Integer.class, keyPrefix + "A"));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, firstPrepay.getPaymentOrderId()));
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, secondPrepay.getPaymentOrderId()));
        }
    }

    @Test
    void shouldMigrateHistoricalAttemptsToExactBinaryUniqueness() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_attempt_exact_migration_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt DROP INDEX uk_attempt_idempotency_bytes");
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt DROP INDEX idx_attempt_idempotency");
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt DROP COLUMN idempotency_key_bytes");
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt ADD UNIQUE KEY uk_attempt_idempotency (idempotency_key)");
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt ALTER COLUMN idempotency_key VARCHAR_IGNORECASE(128)");
            String insertAlias = "INSERT INTO t_payment_attempt (attempt_no, prepay_order_no, payment_order_id, "
                    + "channel_code, payment_method, source_app_id, terminal, client_ip, idempotency_key, "
                    + "request_payload, response_payload, attempt_status, attempt_status_type, created_at) "
                    + "SELECT 'ATT-EXACT-ALIAS', prepay_order_no, payment_order_id, channel_code, "
                    + "payment_method, source_app_id, terminal, client_ip, "
                    + "'pre202607190001|微信|wx_jsapi', request_payload, response_payload, "
                    + "attempt_status, attempt_status_type, created_at FROM t_payment_attempt "
                    + "WHERE attempt_no = 'ATT202607190001'";
            assertThrows(DuplicateKeyException.class, () -> jdbcTemplate.update(insertAlias));
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                        "../database/migrations/2026-10-10-add-payment-attempt-exact-idempotency.sql"));
            }
            assertEquals(512, jdbcTemplate.queryForObject(
                    "SELECT OCTET_LENGTH(idempotency_key_bytes) FROM t_payment_attempt "
                            + "WHERE attempt_no = 'ATT202607190001'", Integer.class));
            assertEquals(1, jdbcTemplate.update(insertAlias));
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE idempotency_key = ?",
                    Integer.class, "PRE202607190001|微信|wx_jsapi"));
            assertEquals(2, jdbcTemplate.queryForObject(
                    "SELECT COUNT(DISTINCT idempotency_key_bytes) FROM t_payment_attempt "
                            + "WHERE attempt_no IN ('ATT202607190001', 'ATT-EXACT-ALIAS')", Integer.class));
        }
    }

    private Object submitAfterStart(PaymentService paymentService, String prepayOrderNo,
                                    String idempotencyKey, String phone,
                                    CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepayOrderNo);
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setSourceAppId("housekeeping-h5-web");
        request.setMerchantNo("MCH_WX_10001");
        request.setAccessToken("token-housekeeping-h5-web");
        request.setTerminal("H5");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone(phone);
        request.setIdempotencyKey(idempotencyKey);
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent submit did not start");
        }
        try {
            return paymentService.submit(request);
        } catch (BusinessException exception) {
            return exception;
        }
    }

    @Test
    void shouldRejectLegacyAttemptWithoutFingerprintOrNewChannelWork() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_replay_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("ALTER TABLE t_payment_attempt DROP COLUMN request_fingerprint");
            try (Connection connection = context.getBean(DataSource.class).getConnection()) {
                ScriptUtils.executeSqlScript(connection, new FileSystemResource(
                        "../database/migrations/2026-10-10-add-payment-attempt-request-fingerprint.sql"));
            }
            PaymentService paymentService = context.getBean(PaymentService.class);
            String paymentOrderId = "PAY202607190002";
            String prepayOrderNo = "PRE202607190002";
            assertTrue(context.getBean(PaymentMapper.class).findDetail(paymentOrderId)
                    .getPrepayAmountBindingValid());
            String idempotencyKey = prepayOrderNo + "|支付宝|alipay_h5";
            assertEquals(1, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ? "
                            + "AND idempotency_key = ?", Integer.class, paymentOrderId, idempotencyKey));
            assertNull(jdbcTemplate.queryForObject(
                    "SELECT request_fingerprint FROM t_payment_attempt WHERE idempotency_key = ?",
                    String.class, idempotencyKey));
            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);
            Integer routeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);

            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo(prepayOrderNo);
            request.setMerchantNo("MCH_ALI_20001");
            BusinessException legacyConflict = assertThrows(BusinessException.class,
                    () -> paymentService.submit(request));
            assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT, legacyConflict.getCode());
            assertEquals(attemptCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals(routeCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));

            PaymentMapper paymentMapper = context.getBean(PaymentMapper.class);
            PrepayOrderDTO originalPrepay = paymentMapper.findPrepay(prepayOrderNo);
            assertEquals(1, paymentMapper.insertPaymentOrder(
                    "PAY-DUPLICATE-SUBMIT", originalPrepay.getOrderNo(),
                    originalPrepay.getCustomerName(), new BigDecimal("6800.00"),
                    originalPrepay.getPayScene()));
            assertEquals(1, paymentMapper.insertPrepayOrder(
                    "PRE-DUPLICATE-SUBMIT", originalPrepay.getBillNo(), originalPrepay.getOrderNo(),
                    originalPrepay.getCustomerName(), new BigDecimal("6800.00"),
                    originalPrepay.getPayScene(), originalPrepay.getCashierTitle(), "PAY-DUPLICATE-SUBMIT",
                    "STANDARD_ORDER"));
            IllegalStateException ambiguous = assertThrows(
                    IllegalStateException.class, () -> paymentService.submit(request));
            assertEquals("active prepay orders for the same order are ambiguous", ambiguous.getMessage());
            assertEquals(attemptCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals(routeCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
        }
    }

    @Test
    void shouldRejectReplayWhenBoundPrepayAndPaymentAmountsDiffer() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_submit_amount_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PaymentService paymentService = context.getBean(PaymentService.class);
            String paymentOrderId = "PAY202607190002";
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET amount = 2000.00 "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertFalse(context.getBean(PaymentMapper.class).findDetail(paymentOrderId)
                    .getPrepayAmountBindingValid());
            assertEquals(0, context.getBean(PaymentMapper.class)
                    .markPaymentSuccessFromChannelResult(paymentOrderId, "ALI77665544"));
            assertEquals("WAIT_CALLBACK", context.getBean(PaymentMapper.class)
                    .findDetail(paymentOrderId).getStatus());
            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);
            Integer routeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);

            PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
            request.setPrepayOrderNo("PRE202607190002");
            request.setMerchantNo("MCH_ALI_20001");
            IllegalStateException replayError = assertThrows(
                    IllegalStateException.class, () -> paymentService.submit(request));
            assertEquals("prepay and payment order amounts are inconsistent", replayError.getMessage());
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET status = 'PREPAY_CREATED' "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET expires_at = "
                    + "TIMESTAMPADD(HOUR, 1, NOW()) WHERE prepay_order_no = ?", request.getPrepayOrderNo()));
            IllegalStateException firstSubmitError = assertThrows(
                    IllegalStateException.class, () -> paymentService.submit(request));
            assertEquals("prepay and payment order amounts are inconsistent", firstSubmitError.getMessage());

            assertEquals(attemptCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals(routeCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
        }
    }
    @Test
    void shouldRejectInvalidPrepayBindingInCashierReuseAndSubmit() throws Exception {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        String databaseUrl = "jdbc:h2:mem:payment_cashier_amount_" + System.nanoTime()
                + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE";
        try (ConfigurableApplicationContext context = application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=" + databaseUrl,
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false")) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            PaymentService paymentService = context.getBean(PaymentService.class);
            String paymentOrderId = "PAY202607190002";
            String prepayOrderNo = "PRE202607190002";
            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET amount = 2000.00 "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertFalse(context.getBean(PaymentMapper.class).findDetail(paymentOrderId)
                    .getPrepayAmountBindingValid());
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.cashier(prepayOrderNo))
                            .getMessage());

            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET expires_at = "
                    + "TIMESTAMPADD(HOUR, 1, NOW()) WHERE prepay_order_no = ?", prepayOrderNo));
            PrepayRequestDTO request = new PrepayRequestDTO();
            request.setOrderNo("ORD202607190002");
            request.setPayScene("H5");
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.prepay(request))
                            .getMessage());

            assertEquals(1, jdbcTemplate.update("UPDATE t_payment_order SET amount = 6800.00 "
                    + "WHERE payment_order_id = ?", paymentOrderId));
            assertEquals("¥6,800.00", paymentService.cashier(prepayOrderNo).getAmount());
            PrepayOrderDTO reusedPrepay = paymentService.prepay(request);
            assertEquals(prepayOrderNo, reusedPrepay.getPrepayOrderNo());
            String serializedPrepay = context.getBean(ObjectMapper.class).writeValueAsString(reusedPrepay);
            assertFalse(serializedPrepay.contains("prepayAmountBindingValid"));
            assertFalse(serializedPrepay.contains("activePrepayCandidateCount"));
            assertFalse(serializedPrepay.contains("sourceKind"));
            PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
            submitRequest.setPrepayOrderNo(prepayOrderNo);
            submitRequest.setMerchantNo("MCH_ALI_20001");
            Integer attemptCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);
            Integer routeCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId);

            assertEquals(1, jdbcTemplate.update("INSERT INTO t_prepay_order "
                    + "(prepay_order_no, bill_no, order_no, customer_name, amount, pay_scene, "
                    + "cashier_title, cashier_status, cashier_status_type, payment_order_id, created_at, expires_at) "
                    + "SELECT 'PRE-DUPLICATE', bill_no, order_no, customer_name, amount, pay_scene, "
                    + "cashier_title, cashier_status, cashier_status_type, payment_order_id, NOW(), expires_at "
                    + "FROM t_prepay_order WHERE prepay_order_no = ?", prepayOrderNo));
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.cashier(prepayOrderNo))
                            .getMessage());
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.prepay(request))
                            .getMessage());
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.submit(submitRequest))
                            .getMessage());
            assertEquals(1, jdbcTemplate.update("DELETE FROM t_prepay_order "
                    + "WHERE prepay_order_no = 'PRE-DUPLICATE'"));
            assertEquals(1, jdbcTemplate.update("UPDATE t_prepay_order SET order_no = 'ORD-OTHER' "
                    + "WHERE prepay_order_no = ?", prepayOrderNo));
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.cashier(prepayOrderNo))
                            .getMessage());
            assertEquals("prepay and payment order amounts are inconsistent",
                    assertThrows(IllegalStateException.class, () -> paymentService.submit(submitRequest))
                            .getMessage());
            assertEquals(attemptCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
            assertEquals(routeCount, jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM t_payment_route_record WHERE payment_order_id = ?",
                    Integer.class, paymentOrderId));
        }
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String body) throws Exception {
        return post(objectMapper, port, "/api/payments/submit", body);
    }

    private JsonNode post(ObjectMapper objectMapper, int port, String path, String body) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + path).openConnection();
        connection.setRequestMethod("POST");
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(20000);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);
        try {
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body.getBytes(StandardCharsets.UTF_8));
            }
            assertEquals(200, connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return objectMapper.readTree(input);
            }
        } finally {
            connection.disconnect();
        }
    }

    private JsonNode get(ObjectMapper objectMapper, int port, String path) throws Exception {
        HttpURLConnection connection = (HttpURLConnection) new URL(
                "http://127.0.0.1:" + port + path).openConnection();
        connection.setConnectTimeout(3000);
        connection.setReadTimeout(5000);
        try {
            assertEquals(200, connection.getResponseCode());
            try (InputStream input = connection.getInputStream()) {
                return objectMapper.readTree(input);
            }
        } finally {
            connection.disconnect();
        }
    }
}
