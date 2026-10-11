package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentCallbackSignatureService;
import com.abc123.hsp.service.PaymentChannelQueryRateLimitService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentChannelRoutingService;
import com.abc123.hsp.service.PaymentChannelSubmitService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.PaymentOpsConfigService;
import com.abc123.hsp.service.PaymentRiskControlService;
import com.abc123.hsp.service.PaymentSubmitRateLimitService;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentRiskSubmitIntegrationTest {

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
    void shouldPersistRiskDecisionsFromPublicSubmitWithoutChannelAttempts() {
        try (ConfigurableApplicationContext context = startContext()) {
            String[] decisions = {"REVIEW", "INTERCEPT", "REJECT"};
            String[] statuses = {"RISK_REVIEW", "RISK_BLOCKED", "RISK_REJECTED"};
            String[] eventTypes = {"PAYMENT_RISK_REVIEW", "PAYMENT_RISK_BLOCKED", "PAYMENT_RISK_REJECTED"};
            String[] cashierStatuses = {"待风控复核", "风控拦截", "风控拒绝"};
            String[] statusTypes = {"warn", "danger", "danger"};
            for (int index = 0; index < decisions.length; index++) {
                PrepayOrderDTO prepay = createPrepay(context, "ORD-RISK-SUBMIT-" + index);
                PaymentChannelSubmitService channelSubmitService = mock(PaymentChannelSubmitService.class);
                PaymentServiceImpl service = riskService(context, decisions[index], channelSubmitService);

                PrepayOrderDTO result = service.submit(submitRequest(prepay));

                assertEquals(prepay.getPrepayOrderNo(), result.getPrepayOrderNo());
                assertEquals(statuses[index], result.getPaymentStatus());
                assertEquals(cashierStatuses[index], result.getCashierStatus());
                assertEquals(statusTypes[index], result.getCashierStatusType());
                assertPaymentFacts(context.getBean(JdbcTemplate.class), prepay,
                        decisions[index], statuses[index], statusTypes[index], cashierStatuses[index], eventTypes[index]);
                verify(channelSubmitService, never()).submit(any());
            }
        }
    }

    @Test
    void shouldRollBackRiskBindingAndStatusWhenEventInsertFails() {
        try (ConfigurableApplicationContext context = startContext()) {
            PrepayOrderDTO prepay = createPrepay(context, "ORD-RISK-ROLLBACK");
            JdbcTemplate jdbc = context.getBean(JdbcTemplate.class);
            String originalChannel = jdbc.queryForObject(
                    "SELECT channel_code FROM t_payment_order WHERE payment_order_id = ?",
                    String.class, prepay.getPaymentOrderId());
            jdbc.execute("ALTER TABLE t_payment_event ADD CONSTRAINT reject_risk_event "
                    + "CHECK (event_type <> 'PAYMENT_RISK_REVIEW')");
            PaymentChannelSubmitService channelSubmitService = mock(PaymentChannelSubmitService.class);
            PaymentServiceImpl service = riskService(context, "REVIEW", channelSubmitService);

            RuntimeException failure = assertThrows(
                    RuntimeException.class, () -> service.submit(submitRequest(prepay)));
            assertEquals("PAYMENT_SUBMIT_TRANSACTION_FAILED:DataIntegrityViolationException", failure.getMessage());

            assertEquals("PREPAY_CREATED", jdbc.queryForObject(
                    "SELECT status FROM t_payment_order WHERE payment_order_id = ?",
                    String.class, prepay.getPaymentOrderId()));
            assertEquals("待选渠道", jdbc.queryForObject(
                    "SELECT payment_method FROM t_payment_order WHERE payment_order_id = ?",
                    String.class, prepay.getPaymentOrderId()));
            assertEquals(originalChannel, jdbc.queryForObject(
                    "SELECT channel_code FROM t_payment_order WHERE payment_order_id = ?",
                    String.class, prepay.getPaymentOrderId()));
            assertEquals("待支付", jdbc.queryForObject(
                    "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = ?",
                    String.class, prepay.getPrepayOrderNo()));
            assertEquals(0, count(jdbc, "t_payment_event", prepay.getPaymentOrderId()));
            assertEquals(0, count(jdbc, "t_payment_process_log", prepay.getPaymentOrderId()));
            assertEquals(0, count(jdbc, "t_payment_attempt", prepay.getPaymentOrderId()));
            verify(channelSubmitService, never()).submit(any());
        }
    }

    private ConfigurableApplicationContext startContext() {
        SpringApplication application = new SpringApplication(HomeServicePaymentApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        return application.run(
                "--spring.profiles.active=demo",
                "--spring.datasource.url=jdbc:h2:mem:payment_risk_submit_" + System.nanoTime()
                        + ";MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE",
                "--payment.ops-config.enabled=false",
                "--payment.scheduling.enabled=false",
                "--spring.rabbitmq.listener.simple.auto-startup=false",
                "--spring.rabbitmq.listener.direct.auto-startup=false");
    }

    private PrepayOrderDTO createPrepay(ConfigurableApplicationContext context, String orderNo) {
        context.getBean(JdbcTemplate.class).update(
                "INSERT INTO t_order (order_no, customer_name, service_type, worker_name, "
                        + "order_amount, paid_amount, order_status, order_status_type, "
                        + "fulfillment_status, fulfillment_status_type, created_at) "
                        + "VALUES (?, '张女士', '家政服务', '李阿姨', 168.00, 0.00, "
                        + "'待支付', 'warn', '已预约', 'info', NOW())", orderNo);
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo(orderNo);
        request.setPayScene("HOME_CLEAN");
        return context.getBean(PaymentServiceImpl.class).prepay(request);
    }

    private PaymentServiceImpl riskService(ConfigurableApplicationContext context,
            String decision, PaymentChannelSubmitService channelSubmitService) {
        PaymentChannelRoutingService routingService = mock(PaymentChannelRoutingService.class);
        PaymentRouteDecisionDTO route = new PaymentRouteDecisionDTO();
        route.setChannelCode("wx_h5");
        when(routingService.resolve(any())).thenReturn(route);
        PaymentRiskControlService riskControlService = mock(PaymentRiskControlService.class);
        PaymentRiskDecisionResultDTO riskDecision = new PaymentRiskDecisionResultDTO();
        riskDecision.setDecision(decision);
        riskDecision.setDecisionType("REVIEW".equals(decision) ? "warn" : "danger");
        riskDecision.setReviewNo("REVIEW-RISK-SUBMIT");
        riskDecision.setEventNo("RISK-EVT-SUBMIT");
        riskDecision.setHitCode("RISK-HIT-SUBMIT");
        when(riskControlService.evaluateSubmitRisk(any())).thenReturn(riskDecision);

        PaymentServiceImpl service = new PaymentServiceImpl(
                context.getBean(PaymentMapper.class),
                context.getBean(PaymentCallbackSignatureService.class),
                routingService,
                context.getBean(PaymentChannelQueryService.class),
                channelSubmitService,
                context.getBean(PaymentEventDispatchService.class),
                riskControlService,
                context.getBean(PaymentOpsConfigService.class),
                context.getBean(PaymentChannelQueryRateLimitService.class),
                context.getBean(PaymentSensitiveDataMasker.class),
                context.getBean(PlatformTransactionManager.class));
        service.setPaymentSubmitRateLimitService(context.getBean(PaymentSubmitRateLimitService.class));
        return service;
    }

    private PaymentSubmitRequestDTO submitRequest(PrepayOrderDTO prepay) {
        PaymentSubmitRequestDTO request = new PaymentSubmitRequestDTO();
        request.setPrepayOrderNo(prepay.getPrepayOrderNo());
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_WX_10001");
        request.setAccessToken("token-housekeeping-h5-web");
        request.setTerminal("H5_WEB");
        request.setClientIp("127.0.0.1");
        return request;
    }

    private void assertPaymentFacts(JdbcTemplate jdbc, PrepayOrderDTO prepay,
            String decision, String status, String statusType, String cashierStatus, String eventType) {
        String paymentOrderId = prepay.getPaymentOrderId();
        assertEquals(status, jdbc.queryForObject(
                "SELECT status FROM t_payment_order WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals("微信支付", jdbc.queryForObject(
                "SELECT payment_method FROM t_payment_order WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals("wx_h5", jdbc.queryForObject(
                "SELECT channel_code FROM t_payment_order WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals(statusType, jdbc.queryForObject(
                "SELECT status_type FROM t_payment_order WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals(cashierStatus, jdbc.queryForObject(
                "SELECT cashier_status FROM t_prepay_order WHERE prepay_order_no = ?",
                String.class, prepay.getPrepayOrderNo()));
        assertEquals(eventType, jdbc.queryForObject(
                "SELECT event_type FROM t_payment_event WHERE payment_order_id = ?", String.class, paymentOrderId));
        assertEquals("PENDING", jdbc.queryForObject(
                "SELECT publish_status FROM t_payment_event WHERE payment_order_id = ?",
                String.class, paymentOrderId));
        assertEquals(1, jdbc.queryForObject(
                "SELECT COUNT(*) FROM t_payment_process_log l JOIN t_payment_event e "
                        + "ON l.source_no = e.event_no WHERE e.payment_order_id = ? "
                        + "AND l.source_type = 'PAYMENT_EVENT'", Integer.class, paymentOrderId));
        assertEquals(0, count(jdbc, "t_payment_attempt", paymentOrderId));
        String payload = jdbc.queryForObject(
                "SELECT event_payload FROM t_payment_event WHERE payment_order_id = ?",
                String.class, paymentOrderId);
        assertTrue(payload.contains("\"decision\":\"" + decision + "\""));
        assertTrue(payload.contains("\"channelCode\":\"wx_h5\""));
    }

    private int count(JdbcTemplate jdbc, String table, String paymentOrderId) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM " + table + " WHERE payment_order_id = ?",
                Integer.class, paymentOrderId);
    }
}
