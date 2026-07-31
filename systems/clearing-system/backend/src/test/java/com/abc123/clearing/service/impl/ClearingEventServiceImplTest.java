package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.service.ShareService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 清分事件服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class ClearingEventServiceImplTest {

    @Autowired
    private ClearingEventServiceImpl clearingEventService;

    @Autowired
    private ShareService shareService;

    @Autowired
    private ClearingOrderServiceImpl clearingOrderService;

    @Test
    void shouldGenerateClearingArtifactsWhenPaymentSuccessConsumed() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200099");
        request.setOrderNo("ORD202607200099");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("王先生");
        request.setMerchantName("浦东门店");
        request.setWorkerName("赵阿姨");
        request.setAmount(new BigDecimal("200.00"));

        ClearingEventDTO result = clearingEventService.consumePaymentSuccess(request);
        PageResultDTO<ShareItemDTO> shares = shareService.list("", "", 1, 20);
        PageResultDTO<ClearingOrderDTO> orders = clearingOrderService.list("", "", "PAY202607200099", "", 1, 20);

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals(6, shares.getTotal());
        assertEquals(1, orders.getTotal());
        assertEquals("PAY202607200099", orders.getItems().get(0).getPaymentOrderId());
    }

    @Test
    void shouldKeepConsumptionIdempotentWhenPaymentSuccessRepeated() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200188");
        request.setOrderNo("ORD202607200188");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("李女士");
        request.setMerchantName("徐汇门店");
        request.setWorkerName("陈阿姨");
        request.setAmount(new BigDecimal("300.00"));

        ClearingEventDTO first = clearingEventService.consumePaymentSuccess(request);
        ClearingEventDTO second = clearingEventService.consumePaymentSuccess(request);
        PageResultDTO<ShareItemDTO> shares = shareService.list("", "", 1, 20);
        PageResultDTO<ClearingOrderDTO> orders = clearingOrderService.list("", "", "PAY202607200188", "", 1, 20);
        PageResultDTO<ClearingEventDTO> events = clearingEventService.list("PAYMENT_SUCCESS", "PAY202607200188", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals(1, orders.getTotal());
        assertEquals(1, events.getTotal());
        assertEquals(6, shares.getTotal());
    }
}
