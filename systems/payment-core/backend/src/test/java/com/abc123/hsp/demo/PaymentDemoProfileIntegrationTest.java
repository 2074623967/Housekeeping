package com.abc123.hsp.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import com.abc123.hsp.HomeServicePaymentApplication;
import com.abc123.hsp.dto.PaymentSubmitRequestDTO;
import com.abc123.hsp.dto.PrepayOrderDTO;
import com.abc123.hsp.dto.PrepayRequestDTO;
import com.abc123.hsp.service.PaymentMetricsService;
import com.abc123.hsp.service.PaymentMonitorService;
import com.abc123.hsp.service.PaymentService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.transaction.support.TransactionTemplate;

class PaymentDemoProfileIntegrationTest {

    @Test
    void shouldLoadPrimaryOperationsViewsFromDemoDatabase() {
        Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        Logger transactionLogger = (Logger) LoggerFactory.getLogger(TransactionTemplate.class);
        Level originalRootLevel = rootLogger.getLevel();
        Level originalTransactionLevel = transactionLogger.getLevel();
        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(HomeServicePaymentApplication.class)
                    .profiles("demo")
                    .properties("payment.scheduling.enabled=false")
                    .web(WebApplicationType.NONE)
                    .run()) {
                assertNotNull(context.getBean(PaymentMetricsService.class).summary());
                assertNotNull(context.getBean(PaymentMonitorService.class).overview().getTrends());
                assertNotNull(context.getBean(PaymentTaskCenterService.class).overview());
                verifyPaymentSubmit(context.getBean(PaymentService.class));
            }
        } finally {
            rootLogger.setLevel(originalRootLevel);
            transactionLogger.setLevel(originalTransactionLevel);
        }
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
}
