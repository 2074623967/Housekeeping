package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.PageResultDTO;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.service.ShareService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 清分事件服务测试。
 */
class ClearingEventServiceImplTest {

    @Test
    void shouldGenerateClearingArtifactsWhenPaymentSuccessConsumed() {
        ClearingMemoryStore store = new ClearingMemoryStore();
        store.initDemoData();
        ClearingMapper mapper = new ClearingMapper();
        ClearingEventServiceImpl eventService = new ClearingEventServiceImpl(store, mapper);
        ShareService shareService = new ShareServiceImpl(store, mapper);

        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY202607200099");
        request.setOrderNo("ORD202607200099");
        request.setBatchDate("2026-07-20");
        request.setCustomerName("王先生");
        request.setMerchantName("浦东门店");
        request.setWorkerName("赵阿姨");
        request.setAmount(new BigDecimal("200.00"));

        ClearingEventDTO result = eventService.consumePaymentSuccess(request);
        PageResultDTO<ShareItemDTO> shares = shareService.list("", "", 1, 20);

        assertEquals("PAYMENT_SUCCESS", result.getEventType());
        assertEquals(6, shares.getItems().size());
    }
}
