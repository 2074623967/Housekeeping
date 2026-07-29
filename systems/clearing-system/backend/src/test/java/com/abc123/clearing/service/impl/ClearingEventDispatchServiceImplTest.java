package com.abc123.clearing.service.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 清分事件下游派发测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingEventDispatchServiceImplTest {

    @Mock
    private ClearingMemoryStore clearingMemoryStore;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldPublishClearingGeneratedToSettlementAndAccounting() {
        ClearingOrderEntity clearingOrder = new ClearingOrderEntity();
        clearingOrder.setClearingNo("CLR-001");
        clearingOrder.setPaymentOrderId("PAY-001");
        clearingOrder.setWorkerAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.orders()).thenReturn(Collections.singletonList(clearingOrder));

        ShareItemEntity workerShare = new ShareItemEntity();
        workerShare.setShareType("WORKER");
        workerShare.setShareTargetNo("WRK-001");
        workerShare.setShareTargetName("李阿姨");
        workerShare.setShareAmount(new BigDecimal("120.00"));
        when(clearingMemoryStore.sharesByClearingNo("CLR-001")).thenReturn(Collections.singletonList(workerShare));

        when(restTemplate.postForEntity(eq("http://settlement"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://accounting"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("ok", HttpStatus.OK));

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        boolean result = dispatchService.publishClearingGenerated("PAY-001");

        assertTrue(result);
        verify(restTemplate, times(1)).postForEntity(eq("http://settlement"), any(), eq(String.class));
        verify(restTemplate, times(1)).postForEntity(eq("http://accounting"), any(), eq(String.class));
    }

    @Test
    void shouldReturnFalseWhenPaymentOrderMissing() {
        when(clearingMemoryStore.orders()).thenReturn(Collections.<ClearingOrderEntity>emptyList());

        ClearingEventDispatchServiceImpl dispatchService = new ClearingEventDispatchServiceImpl(
                clearingMemoryStore,
                "http://settlement",
                "http://accounting",
                "ACT10002",
                restTemplate);

        assertFalse(dispatchService.publishClearingGenerated("PAY-NOT-FOUND"));
    }
}
