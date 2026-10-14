package com.abc123.hsp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.BillDetailDTO;
import com.abc123.hsp.dto.BillQueryDTO;
import com.abc123.hsp.dto.OrderDetailDTO;
import com.abc123.hsp.dto.PaymentCallbackRequestDTO;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentQueryRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.service.BillService;
import com.abc123.hsp.service.OrderService;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.PaymentChannelQueryService;
import com.abc123.hsp.service.PaymentIssueService;
import com.abc123.hsp.service.PaymentMetricsService;
import com.abc123.hsp.service.PaymentMonitorService;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentDemoProfileIntegrationTest {

    @Test
    void shouldLoadPrimaryOperationsViewsFromDemoDatabase() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger transactionLogger = (Logger) LoggerFactory.getLogger(TransactionTemplate.class);
        Level originalRootLevel = rootLogger.getLevel();
        Level originalTransactionLevel = transactionLogger.getLevel();
        PaymentEventDispatchService dispatchService = mock(PaymentEventDispatchService.class);
        PaymentChannelQueryService queryService = mock(PaymentChannelQueryService.class);
        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(HomeServicePaymentApplication.class)
                    .profiles("demo")
                    .properties("payment.scheduling.enabled=false")
                    .web(WebApplicationType.NONE)
                    .initializers(applicationContext -> {
                        GenericApplicationContext registry = (GenericApplicationContext) applicationContext;
                        registry.registerBean("stubPaymentEventDispatchService", PaymentEventDispatchService.class,
                                () -> dispatchService, definition -> definition.setPrimary(true));
                        registry.registerBean("stubPaymentChannelQueryService", PaymentChannelQueryService.class,
                                () -> queryService, definition -> definition.setPrimary(true));
                    })
                    .run()) {
                assertNotNull(context.getBean(PaymentMetricsService.class).summary());
                assertNotNull(context.getBean(PaymentMonitorService.class).overview().getTrends());
                assertNotNull(context.getBean(PaymentTaskCenterService.class).overview());
                assertNotNull(context.getBean(PaymentIssueService.class).list(new PaymentIssueQueryDTO()).getItems());
                verifyPaymentSubmit(context.getBean(PaymentService.class));
                verifyPcAlipaySubmit(context.getBean(PaymentService.class));
                verifyMiniProgramWechatSubmit(context.getBean(PaymentService.class), context.getBean(JdbcTemplate.class));
                verifyApiAlipaySubmit(context.getBean(PaymentService.class), context.getBean(JdbcTemplate.class));
                verifyNewStandardOrderPayment(context);
                verifyPartialStandardOrderBill(context);
                verifyStandardOrderSettlement(context, dispatchService, queryService);
            }
        } finally {
            rootLogger.setLevel(originalRootLevel);
            transactionLogger.setLevel(originalTransactionLevel);
        }
    }

    private void verifyNewStandardOrderPayment(ConfigurableApplicationContext context) {
        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        PaymentService paymentService = context.getBean(PaymentService.class);
        OrderService orderService = context.getBean(OrderService.class);
        BillService billService = context.getBean(BillService.class);
        String orderNo = "DEMO-INTEGRATION-STANDARD-001";
        String staleOrderNo = "DEMO-INTEGRATION-STANDARD-STALE";
        assertEquals(1, jdbcTemplate.update("INSERT INTO t_order (order_no, customer_name, service_type, "
                + "worker_name, order_amount, paid_amount, order_status, order_status_type, "
                + "fulfillment_status, fulfillment_status_type, created_at) "
                + "VALUES (?, 'demo standard customer', '深度保洁', 'demo worker', 88.00, 0.00, "
                + "'待支付', 'warn', '待确认', 'warn', CURRENT_TIMESTAMP)", staleOrderNo));
        assertEquals(1, jdbcTemplate.update("INSERT INTO t_bill (bill_no, order_no, customer_name, "
                + "bill_amount, paid_amount, bill_status, bill_status_type, due_at, created_at) "
                + "VALUES ('BILL-INTEGRATION-STANDARD-STALE', ?, 'demo standard customer', "
                + "88.00, 1.00, '部分支付', 'warn', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)", staleOrderNo));
        PrepayRequestDTO staleRequest = new PrepayRequestDTO();
        staleRequest.setOrderNo(staleOrderNo);
        staleRequest.setPayScene("HOME_CLEAN");
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.prepay(staleRequest)).getCode());
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_order WHERE order_no = ?", Integer.class, staleOrderNo));

        assertEquals(1, jdbcTemplate.update("INSERT INTO t_order (order_no, customer_name, service_type, "
                + "worker_name, order_amount, paid_amount, order_status, order_status_type, "
                + "fulfillment_status, fulfillment_status_type, created_at) "
                + "VALUES (?, 'demo standard customer', '深度保洁', 'demo worker', 88.00, 0.00, "
                + "'待支付', 'warn', '待确认', 'warn', CURRENT_TIMESTAMP)", orderNo));

        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo(orderNo);
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("demo standard customer");
        request.setAmount(new BigDecimal("88.00"));
        request.setCashierTitle("standard order payment");
        PrepayOrderDTO prepay = paymentService.prepay(request);
        assertEquals("STANDARD_ORDER", prepay.getSourceKind());
        assertEquals(prepay.getPrepayOrderNo(), paymentService.prepay(request).getPrepayOrderNo());
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET paid_amount = 1.00 WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.prepay(request)).getCode());
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET paid_amount = 0.00 WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_status = '部分支付' WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.prepay(request)).getCode());
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_status = '待支付' WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals("¥0.00", orderService.detail(orderNo).getPaidAmount());
        assertEquals(orderNo, billService.detail(prepay.getBillNo()).getStandardOrderNo());

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("微信支付");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setSourceAppId("housekeeping-h5-web");
        submitRequest.setMerchantNo("MCH_WX_10001");
        submitRequest.setAccessToken("token-housekeeping-h5-web");
        submitRequest.setTerminal("H5");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-standard-integration");
        submitRequest.setPayerPhone("13800000000");
        submitRequest.setIdempotencyKey(orderNo);
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_amount = 89.00 WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.submit(submitRequest)).getCode());
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                Integer.class, prepay.getPaymentOrderId()));
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_amount = 88.00 WHERE bill_no = ?",
                prepay.getBillNo()));
        PrepayOrderDTO submitted = paymentService.submit(submitRequest);
        assertEquals("WAIT_CALLBACK", paymentService.detail(submitted.getPaymentOrderId()).getStatus());

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId(submitted.getPaymentOrderId());
        callback.setChannelTransactionNo(submitted.getChannelTransactionNo());
        callback.setTradeStatus("SUCCESS");
        assertEquals("SUCCESS", paymentService.callback("wx_h5", callback).getStatus());
        assertEquals("SUCCESS", paymentService.callback("wx_h5", callback).getStatus());

        OrderDetailDTO order = orderService.detail(orderNo);
        BillDetailDTO bill = billService.detail(prepay.getBillNo());
        assertEquals("¥88.00", order.getPaidAmount());
        assertEquals("¥0.00", order.getOutstandingAmount());
        assertEquals("待履约", order.getOrderStatus());
        assertEquals(submitted.getPaymentOrderId(), order.getLatestPaymentOrderId());
        assertEquals("¥88.00", bill.getPaidAmount());
        assertEquals("¥0.00", bill.getUnpaidAmount());
        assertEquals("已结清", bill.getBillStatus());
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                        + "AND event_type = 'PAYMENT_SUCCESS'",
                Integer.class, submitted.getPaymentOrderId()));
    }

    private void verifyPartialStandardOrderBill(ConfigurableApplicationContext context) {
        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        String orderNo = "DEMO-INTEGRATION-STANDARD-PARTIAL";
        assertEquals(1, jdbcTemplate.update("INSERT INTO t_order (order_no, customer_name, service_type, "
                + "worker_name, order_amount, paid_amount, order_status, order_status_type, "
                + "fulfillment_status, fulfillment_status_type, created_at) "
                + "VALUES (?, 'demo partial customer', '深度保洁', 'demo worker', 88.00, 20.00, "
                + "'待支付', 'warn', '待确认', 'warn', CURRENT_TIMESTAMP)", orderNo));
        PrepayRequestDTO request = new PrepayRequestDTO();
        request.setOrderNo(orderNo);
        request.setPayScene("HOME_CLEAN");
        request.setCustomerName("demo partial customer");
        request.setAmount(new BigDecimal("68.00"));
        PrepayOrderDTO prepay = context.getBean(PaymentService.class).prepay(request);
        BillDetailDTO bill = context.getBean(BillService.class).detail(prepay.getBillNo());
        assertEquals(orderNo, bill.getStandardOrderNo());
        assertEquals("¥88.00", bill.getBillAmount());
        assertEquals("¥20.00", bill.getPaidAmount());
        assertEquals("¥68.00", bill.getUnpaidAmount());
        assertEquals("部分支付", bill.getBillStatus());
        assertEquals("warn", bill.getBillStatusType());
        BillQueryDTO billQuery = new BillQueryDTO();
        billQuery.setOrderNo(orderNo);
        assertEquals(Long.valueOf(1), context.getBean(BillService.class).overview(billQuery).getPartialPaidBillCount());
        assertEquals(Long.valueOf(0), context.getBean(BillService.class).overview(billQuery).getUnpaidBillCount());

        PaymentService paymentService = context.getBean(PaymentService.class);
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_status = '待支付' WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.prepay(request)).getCode());

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("微信支付");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setSourceAppId("housekeeping-h5-web");
        submitRequest.setMerchantNo("MCH_WX_10001");
        submitRequest.setAccessToken("token-housekeeping-h5-web");
        submitRequest.setTerminal("H5");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-partial-integration");
        submitRequest.setPayerPhone("13800000000");
        submitRequest.setIdempotencyKey(orderNo);
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.submit(submitRequest)).getCode());
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                Integer.class, prepay.getPaymentOrderId()));

        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_status = '部分支付', "
                + "bill_status_type = 'success' WHERE bill_no = ?", prepay.getBillNo()));
        assertEquals(ErrorCode.PAYMENT_PREPAY_REQUEST_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.prepay(request)).getCode());
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET bill_status_type = 'warn' WHERE bill_no = ?",
                prepay.getBillNo()));
        assertEquals(prepay.getPrepayOrderNo(), paymentService.prepay(request).getPrepayOrderNo());
        assertEquals("WAIT_CALLBACK", paymentService.submit(submitRequest).getPaymentStatus());
    }

    private void verifyStandardOrderSettlement(
            ConfigurableApplicationContext context, PaymentEventDispatchService dispatchService,
            PaymentChannelQueryService queryService) {
        OrderService orderService = context.getBean(OrderService.class);
        BillService billService = context.getBean(BillService.class);
        PaymentService paymentService = context.getBean(PaymentService.class);
        OrderDetailDTO beforeOrder = orderService.detail("ORD202607190002");
        BillDetailDTO beforeBill = billService.detail("BILL202607190002");
        assertEquals("¥8,800.00", beforeOrder.getOrderAmount());
        assertEquals("¥2,000.00", beforeOrder.getPaidAmount());
        assertEquals("¥6,800.00", beforeOrder.getOutstandingAmount());
        assertEquals("部分支付", beforeBill.getBillStatus());
        assertEquals("¥2,000.00", beforeBill.getPaidAmount());
        assertEquals("¥6,800.00", beforeBill.getUnpaidAmount());

        JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
        assertEquals(1, jdbcTemplate.update("UPDATE t_order SET paid_amount = 2050.00 "
                + "WHERE order_no = 'ORD202607190002'"));
        PaymentChannelQueryResultDTO queryResult = new PaymentChannelQueryResultDTO();
        queryResult.setTradeStatus("SUCCESS");
        queryResult.setChannelTransactionNo("ALI77665544");
        queryResult.setQuerySource("LOCAL_SIMULATION");
        when(queryService.query(any(PaymentDetailDTO.class))).thenReturn(queryResult);
        PaymentQueryRequestDTO queryRequest = new PaymentQueryRequestDTO();
        queryRequest.setPaymentOrderId("PAY202607190002");
        assertEquals("payment settlement standard order facts have changed",
                assertThrows(IllegalStateException.class, () -> paymentService.query(queryRequest)).getMessage());
        assertEquals("WAIT_CALLBACK", paymentService.detail("PAY202607190002").getStatus());
        assertEquals("STANDARD_ORDER", paymentService.detail("PAY202607190002").getSourceKind());
        assertEquals(new BigDecimal("2050.00"), jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM t_order WHERE order_no = 'ORD202607190002'", BigDecimal.class));
        assertEquals(new BigDecimal("2000.00"), jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM t_bill WHERE bill_no = 'BILL202607190002'", BigDecimal.class));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                        + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, "PAY202607190002"));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ? "
                        + "AND completed_at IS NOT NULL", Integer.class, "PAY202607190002"));
        assertEquals(1, jdbcTemplate.update("UPDATE t_order SET paid_amount = 2000.00 "
                + "WHERE order_no = 'ORD202607190002'"));

        PaymentCallbackRequestDTO callback = new PaymentCallbackRequestDTO();
        callback.setPaymentOrderId("PAY202607190002");
        callback.setChannelTransactionNo("ALI77665544");
        callback.setTradeStatus("SUCCESS");
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET paid_amount = 2050.00 "
                + "WHERE bill_no = 'BILL202607190002'"));
        assertEquals("payment bill settlement was not applied atomically",
                assertThrows(IllegalStateException.class,
                        () -> paymentService.callback("alipay_h5", callback)).getMessage());
        assertEquals("WAIT_CALLBACK", paymentService.detail("PAY202607190002").getStatus());
        assertEquals(new BigDecimal("2000.00"), jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM t_order WHERE order_no = 'ORD202607190002'", BigDecimal.class));
        assertEquals(new BigDecimal("2050.00"), jdbcTemplate.queryForObject(
                "SELECT paid_amount FROM t_bill WHERE bill_no = 'BILL202607190002'", BigDecimal.class));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ? "
                        + "AND completed_at IS NOT NULL", Integer.class, "PAY202607190002"));
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                        + "AND event_type = 'PAYMENT_SUCCESS'", Integer.class, "PAY202607190002"));
        assertEquals(1, jdbcTemplate.update("UPDATE t_bill SET paid_amount = 2000.00 "
                + "WHERE bill_no = 'BILL202607190002'"));
        PaymentDetailDTO paid = paymentService.callback("alipay_h5", callback);
        assertEquals("SUCCESS", paid.getStatus());
        assertEquals("¥6,800.00", paid.getAmount());
        assertFalse(context.getBean(ObjectMapper.class).valueToTree(paid).has("sourceKind"));

        OrderDetailDTO settledOrder = orderService.detail("ORD202607190002");
        BillDetailDTO settledBill = billService.detail("BILL202607190002");
        assertEquals("¥8,800.00", settledOrder.getPaidAmount());
        assertEquals("¥0.00", settledOrder.getOutstandingAmount());
        assertEquals("待履约", settledOrder.getOrderStatus());
        assertEquals("PAY202607190002", settledOrder.getLatestPaymentOrderId());
        assertEquals("SUCCESS", settledOrder.getLatestPaymentStatus());
        assertEquals("BILL202607190002", settledBill.getBillNo());
        assertEquals("ORD202607190002", settledBill.getStandardOrderNo());
        assertEquals("¥8,800.00", settledBill.getPaidAmount());
        assertEquals("¥0.00", settledBill.getUnpaidAmount());
        assertEquals("已结清", settledBill.getBillStatus());
        assertEquals("success", settledBill.getBillStatusType());
        assertEquals("PAY202607190002", settledBill.getLatestPaymentOrderId());
        assertEquals("SUCCESS", settledBill.getLatestPaymentStatus());

        assertEquals("SUCCESS", paymentService.callback("alipay_h5", callback).getStatus());
        assertEquals("¥8,800.00", orderService.detail("ORD202607190002").getPaidAmount());
        assertEquals("¥8,800.00", billService.detail("BILL202607190002").getPaidAmount());
        assertEquals(Integer.valueOf(1), context.getBean(JdbcTemplate.class).queryForObject(
                "SELECT COUNT(*) FROM t_payment_event WHERE payment_order_id = ? "
                        + "AND event_type = 'PAYMENT_SUCCESS'",
                Integer.class, "PAY202607190002"));
        verify(dispatchService, times(1))
                .publishPaymentSuccess(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.eq("PAY202607190002"));
    }

    private void verifyPaymentSubmit(PaymentService paymentService) {
        PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
        prepayRequest.setOrderNo("DEMO-INTEGRATION-PAYMENT-001");
        prepayRequest.setPayScene("HOME_CLEAN");
        prepayRequest.setCustomerName("demo integration");
        prepayRequest.setAmount(new BigDecimal("88.00"));
        prepayRequest.setCashierTitle("home service payment");
        PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("微信支付");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setSourceAppId("housekeeping-h5-web");
        submitRequest.setMerchantNo("MCH_WX_10001");
        submitRequest.setAccessToken("token-housekeeping-h5-web");
        submitRequest.setTerminal("H5");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-integration");
        submitRequest.setPayerPhone("13800000000");
        submitRequest.setIdempotencyKey("DEMO-INTEGRATION-PAYMENT-001");

        PrepayOrderDTO submitted = paymentService.submit(submitRequest);
        assertEquals("支付中", submitted.getCashierStatus());
        assertEquals("WAIT_CALLBACK", paymentService.detail(submitted.getPaymentOrderId()).getStatus());
    }

    private void verifyPcAlipaySubmit(PaymentService paymentService) {
        PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
        prepayRequest.setOrderNo("DEMO-INTEGRATION-PC-ALIPAY-001");
        prepayRequest.setPayScene("BALANCE_PAY_PC");
        prepayRequest.setCustomerName("demo pc integration");
        prepayRequest.setAmount(new BigDecimal("68.00"));
        prepayRequest.setCashierTitle("pc payment");
        PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("支付宝");
        submitRequest.setChannelCode("alipay_h5");
        submitRequest.setSourceAppId("housekeeping-pc-web");
        submitRequest.setMerchantNo("MCH_ALI_20001");
        submitRequest.setAccessToken("token-housekeeping-pc-web");
        submitRequest.setTerminal("PC");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-pc-integration");
        submitRequest.setPayerPhone("13800000001");
        submitRequest.setIdempotencyKey("DEMO-INTEGRATION-PC-ALIPAY-001");

        PrepayOrderDTO submitted = paymentService.submit(submitRequest);
        PaymentDetailDTO detail = paymentService.detail(submitted.getPaymentOrderId());
        assertEquals("WAIT_CALLBACK", detail.getStatus());
        assertEquals("WAIT_BUYER_PAY", detail.getChannelReturnCode());
        assertEquals("PAYMENT-CHANNEL-2001", detail.getStandardizedCode());
        assertEquals("是", detail.getRetryable());
        assertEquals("否", detail.getManualInterventionRequired());
    }

    private void verifyMiniProgramWechatSubmit(PaymentService paymentService, JdbcTemplate jdbcTemplate) {
        PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
        prepayRequest.setOrderNo("DEMO-INTEGRATION-MINI-WECHAT-001");
        prepayRequest.setPayScene("BALANCE_PAY_MINI_PROGRAM");
        prepayRequest.setCustomerName("demo mini program integration");
        prepayRequest.setAmount(new BigDecimal("68.00"));
        prepayRequest.setCashierTitle("mini program payment");
        PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("微信支付");
        submitRequest.setChannelCode("wx_h5");
        submitRequest.setSourceAppId("housekeeping-mini-program");
        submitRequest.setMerchantNo("MCH_WX_10001");
        submitRequest.setAccessToken("token-housekeeping-mini-program");
        submitRequest.setTerminal("WECHAT_MINI_PROGRAM");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-mini-program-integration");
        submitRequest.setPayerPhone("13800000002");
        submitRequest.setIdempotencyKey("MINI-" + prepay.getPrepayOrderNo());

        PrepayOrderDTO submitted = paymentService.submit(submitRequest);
        PrepayOrderDTO repeated = paymentService.submit(submitRequest);
        PaymentDetailDTO detail = paymentService.detail(submitted.getPaymentOrderId());
        assertEquals("支付中", submitted.getCashierStatus());
        assertEquals(submitted.getPaymentOrderId(), repeated.getPaymentOrderId());
        assertEquals(submitted.getPrepayOrderNo(), repeated.getPrepayOrderNo());
        assertEquals("WAIT_CALLBACK", detail.getStatus());
        assertEquals("MINI_PROGRAM", detail.getLatestTerminal());
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ? AND idempotency_key = ?",
                Integer.class, prepay.getPaymentOrderId(), submitRequest.getIdempotencyKey()));
        submitRequest.setPayerPhone("13800000003");
        assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.submit(submitRequest)).getCode());
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                Integer.class, prepay.getPaymentOrderId()));
    }

    private void verifyApiAlipaySubmit(PaymentService paymentService, JdbcTemplate jdbcTemplate) {
        PrepayRequestDTO prepayRequest = new PrepayRequestDTO();
        prepayRequest.setOrderNo("DEMO-INTEGRATION-API-ALIPAY-001");
        prepayRequest.setPayScene("SERVICE_ORDER_API");
        prepayRequest.setCustomerName("demo api integration");
        prepayRequest.setAmount(new BigDecimal("168.00"));
        prepayRequest.setCashierTitle("api payment");
        PrepayOrderDTO prepay = paymentService.prepay(prepayRequest);

        PaymentSubmitRequestDTO submitRequest = new PaymentSubmitRequestDTO();
        submitRequest.setPrepayOrderNo(prepay.getPrepayOrderNo());
        submitRequest.setPaymentMethod("支付宝");
        submitRequest.setChannelCode("alipay_h5");
        submitRequest.setSourceAppId("housekeeping-api");
        submitRequest.setMerchantNo("MCH_ALI_20001");
        submitRequest.setAccessToken("token-housekeeping-api");
        submitRequest.setTerminal("API");
        submitRequest.setClientIp("127.0.0.1");
        submitRequest.setClientDeviceId("demo-api-integration");
        submitRequest.setIdempotencyKey("API-" + prepay.getPrepayOrderNo());

        PrepayOrderDTO submitted = paymentService.submit(submitRequest);
        PrepayOrderDTO repeated = paymentService.submit(submitRequest);
        PaymentDetailDTO detail = paymentService.detail(submitted.getPaymentOrderId());
        assertEquals("支付中", submitted.getCashierStatus());
        assertEquals(submitted.getPaymentOrderId(), repeated.getPaymentOrderId());
        assertEquals("WAIT_CALLBACK", detail.getStatus());
        assertEquals("API", detail.getLatestTerminal());
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ? AND idempotency_key = ?",
                Integer.class, prepay.getPaymentOrderId(), submitRequest.getIdempotencyKey()));
        submitRequest.setClientDeviceId("different-api-device");
        assertEquals(ErrorCode.PAYMENT_SUBMIT_IDEMPOTENCY_KEY_CONFLICT,
                assertThrows(BusinessException.class, () -> paymentService.submit(submitRequest)).getCode());
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM t_payment_attempt WHERE payment_order_id = ?",
                Integer.class, prepay.getPaymentOrderId()));
    }
}
