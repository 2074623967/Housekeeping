package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.entity.SettlementEventEntity;
import com.abc123.settlement.entity.SettlementOrderEntity;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 结算对账事实失败边界测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementReconciliationFailureTest {

    @Mock
    private SettlementMemoryStore settlementMemoryStore;

    @Mock
    private SettlementMapper settlementMapper;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldKeepClearingConsumptionRetryableWhenFactReportFails() {
        ClearingGeneratedEventRequestDTO request = request();
        SettlementEventEntity event = new SettlementEventEntity();
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.setBusinessChainId("CHAIN-PAY-001");
        order.setSettlementNo("SLT-001");
        order.setNetSettleAmount(new BigDecimal("90.00"));
        when(settlementMemoryStore.consumeClearingGenerated(request)).thenReturn(event);
        when(settlementMapper.toEventDTO(event)).thenReturn(null);
        when(settlementMemoryStore.findOrderByClearingTarget("CLR-001", "WORKER", "WRK-001"))
                .thenReturn(order);
        Map<String, Object> responseBody = new HashMap<String, Object>();
        responseBody.put("code", "RECONCILIATION_UNAVAILABLE");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<Map>(responseBody, HttpStatus.OK));

        SettlementEventServiceImpl service = new SettlementEventServiceImpl(
                settlementMemoryStore, settlementMapper, restTemplate);
        setField(service, "reconciliationFactUrl", "http://reconciliation/facts");

        assertThrows(IllegalStateException.class, () -> service.consumeClearingGenerated(request));
    }

    @Test
    void shouldRejectMissingSettlementOrderWhenReconciliationIsConfigured() {
        ClearingGeneratedEventRequestDTO request = request();
        when(settlementMemoryStore.consumeClearingGenerated(request)).thenReturn(new SettlementEventEntity());

        assertThrows(IllegalStateException.class, () -> service().consumeClearingGenerated(request));

        verify(restTemplate, never()).postForEntity(any(), any(), eq(Map.class));
    }

    @Test
    void shouldAcceptReconciliationSnapshotContainingThisSettlementFact() {
        ClearingGeneratedEventRequestDTO request = request();
        SettlementOrderEntity order = stubConsumedOrder(request);
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response(order, request, "CHAIN-PAY-001", new BigDecimal("90.00")));

        assertDoesNotThrow(() -> service().consumeClearingGenerated(request));

        verify(restTemplate).postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class));
    }

    @Test
    void shouldRejectReconciliationSnapshotForAnotherChain() {
        ClearingGeneratedEventRequestDTO request = request();
        SettlementOrderEntity order = stubConsumedOrder(request);
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response(order, request, "CHAIN-OTHER", new BigDecimal("90.00")));

        assertThrows(IllegalStateException.class, () -> service().consumeClearingGenerated(request));
    }

    @Test
    void shouldRejectReconciliationSnapshotWithWrongSettlementAmount() {
        ClearingGeneratedEventRequestDTO request = request();
        SettlementOrderEntity order = stubConsumedOrder(request);
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response(order, request, "CHAIN-PAY-001", new BigDecimal("91.00")));

        assertThrows(IllegalStateException.class, () -> service().consumeClearingGenerated(request));
    }

    @Test
    void shouldRejectSuccessfulCodeWithoutStructuredSnapshot() {
        ClearingGeneratedEventRequestDTO request = request();
        stubConsumedOrder(request);
        Map<String, Object> body = new HashMap<String, Object>();
        body.put("code", "0");
        body.put("data", new Object());
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<Map>(body, HttpStatus.OK));

        assertThrows(IllegalStateException.class, () -> service().consumeClearingGenerated(request));
    }

    private SettlementOrderEntity stubConsumedOrder(ClearingGeneratedEventRequestDTO request) {
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.setBusinessChainId("CHAIN-PAY-001");
        order.setSettlementNo("SLT-001");
        order.setNetSettleAmount(new BigDecimal("90.00"));
        when(settlementMemoryStore.consumeClearingGenerated(request)).thenReturn(new SettlementEventEntity());
        when(settlementMemoryStore.findOrderByClearingTarget("CLR-001", "WORKER", "WRK-001"))
                .thenReturn(order);
        return order;
    }

    private SettlementEventServiceImpl service() {
        SettlementEventServiceImpl service = new SettlementEventServiceImpl(
                settlementMemoryStore, settlementMapper, restTemplate);
        setField(service, "reconciliationFactUrl", "http://reconciliation/facts");
        return service;
    }

    private ResponseEntity<Map> response(SettlementOrderEntity order, ClearingGeneratedEventRequestDTO request,
                                         String businessChainId, BigDecimal amount) {
        Map<String, Object> fact = new LinkedHashMap<String, Object>();
        fact.put("businessChainId", businessChainId);
        fact.put("systemCode", "settlement-system");
        fact.put("factType", "SETTLEMENT_CREATED");
        fact.put("factNo", order.getSettlementNo());
        fact.put("businessNo", request.getPaymentOrderId());
        fact.put("payScene", request.getPayScene());
        fact.put("factStatus", "SUCCESS");
        fact.put("amount", amount);
        Map<String, Object> snapshot = new LinkedHashMap<String, Object>();
        snapshot.put("businessChainId", businessChainId);
        snapshot.put("facts", Collections.singletonList(fact));
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "0");
        body.put("data", snapshot);
        return new ResponseEntity<Map>(body, HttpStatus.OK);
    }

    private ClearingGeneratedEventRequestDTO request() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLR-001");
        request.setPaymentOrderId("PAY-001");
        request.setPayScene("HOME_CLEAN");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-001");
        request.setTargetName("李阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));
        return request;
    }

    private void setField(Object target, String fieldName, Object value) {
        try {
            java.lang.reflect.Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("无法设置测试字段 " + fieldName, exception);
        }
    }
}
