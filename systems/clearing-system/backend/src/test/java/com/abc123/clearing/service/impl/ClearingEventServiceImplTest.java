package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingEventDTO;
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

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals(6, shares.getTotal());
    }
}
