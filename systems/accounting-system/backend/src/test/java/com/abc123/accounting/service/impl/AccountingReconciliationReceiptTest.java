package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.entity.AccountEventEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

@ExtendWith(MockitoExtension.class)
class AccountingReconciliationReceiptTest {

    @Mock
    private AccountingMemoryStore accountingMemoryStore;

    @Mock
    private AccountingMapper accountingMapper;

    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldAcceptOwnPaymentFactInReconciliationSnapshot() {
        stubEvent("PAYMENT_SUCCESS", "PAY-001");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-PAY-001", "AE-001", "PAY-001", new BigDecimal("20.00")));

        AccountEventDTO result = service().consumePaymentSuccess(paymentRequest());

        assertEquals("AE-001", result.getEventNo());
    }

    @Test
    void shouldEscapeSummaryInReconciliationFactPayload() throws Exception {
        String summary = "账务\\事件\"摘要\n下一行";
        stubEvent("PAYMENT_SUCCESS", "PAY-001").setSummary(summary);
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-PAY-001", "AE-001", "PAY-001", new BigDecimal("20.00")));

        service().consumePaymentSuccess(paymentRequest());

        ArgumentCaptor<HttpEntity> requestCaptor = ArgumentCaptor.forClass(HttpEntity.class);
        verify(restTemplate).postForEntity(eq("http://reconciliation/facts"), requestCaptor.capture(), eq(Map.class));
        ObjectMapper mapper = new ObjectMapper();
        JsonNode outerPayload = mapper.readTree((String) requestCaptor.getValue().getBody());
        JsonNode factPayload = mapper.readTree(outerPayload.get("payload").asText());
        assertEquals("AE-001", factPayload.get("eventNo").asText());
        assertEquals(summary, factPayload.get("summary").asText());
    }

    @Test
    void shouldAcceptRefundFactWithOriginalPaymentBusinessNumber() {
        stubEvent("REFUND_SUCCESS", "REF-001");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-PAY-001", "AE-001", "PAY-001", new BigDecimal("20.00")));

        AccountEventDTO result = service().consumeRefundSuccess(refundRequest());

        assertEquals("AE-001", result.getEventNo());
    }

    @Test
    void shouldRejectReconciliationReceiptForAnotherBusinessChain() {
        stubEvent("PAYMENT_SUCCESS", "PAY-001");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-OTHER", "AE-001", "PAY-001", new BigDecimal("20.00")));

        assertThrows(IllegalStateException.class, () -> service().consumePaymentSuccess(paymentRequest()));
    }

    @Test
    void shouldRejectReconciliationReceiptForAnotherAccountingFact() {
        stubEvent("PAYMENT_SUCCESS", "PAY-001");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-PAY-001", "AE-OTHER", "PAY-001", new BigDecimal("20.00")));

        assertThrows(IllegalStateException.class, () -> service().consumePaymentSuccess(paymentRequest()));
    }

    @Test
    void shouldRejectReconciliationReceiptWithWrongAmount() {
        stubEvent("PAYMENT_SUCCESS", "PAY-001");
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(response("CHAIN-PAY-001", "AE-001", "PAY-001", new BigDecimal("21.00")));

        assertThrows(IllegalStateException.class, () -> service().consumePaymentSuccess(paymentRequest()));
    }

    @Test
    void shouldRejectSuccessfulCodeWithoutStructuredSnapshot() {
        stubEvent("PAYMENT_SUCCESS", "PAY-001");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("code", "0");
        body.put("data", new Object());
        when(restTemplate.postForEntity(eq("http://reconciliation/facts"), any(), eq(Map.class)))
                .thenReturn(new ResponseEntity<Map>(body, HttpStatus.OK));

        assertThrows(IllegalStateException.class, () -> service().consumePaymentSuccess(paymentRequest()));
    }

    private AccountEventDTO stubEvent(String eventType, String bizNo) {
        AccountEventEntity entity = new AccountEventEntity();
        AccountEventDTO event = new AccountEventDTO();
        event.setBusinessChainId("CHAIN-PAY-001");
        event.setEventNo("AE-001");
        event.setEventType(eventType);
        event.setBizNo(bizNo);
        event.setSummary("账务消费事件");
        when(accountingMemoryStore.recordEvent(eq(eventType), eq(bizNo), any(), any(), eq("CHAIN-PAY-001")))
                .thenReturn(entity);
        when(accountingMapper.toEventDTO(entity)).thenReturn(event);
        return event;
    }

    private AccountingEventServiceImpl service() {
        AccountingEventServiceImpl service = new AccountingEventServiceImpl(
                accountingMemoryStore, accountingMapper, "http://reconciliation/facts", restTemplate);
        org.springframework.test.util.ReflectionTestUtils.setField(
                service, "reconciliationFactSecret", "local-accounting-fact-secret");
        return service;
    }

    private PaymentSuccessEventRequestDTO paymentRequest() {
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setPaymentOrderId("PAY-001");
        request.setOrderNo("ORD-001");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("20.00"));
        return request;
    }

    private RefundSuccessEventRequestDTO refundRequest() {
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        request.setAccountNo("ACT10001");
        request.setRefundOrderId("REF-001");
        request.setPaymentOrderId("PAY-001");
        request.setPayScene("HOME_CLEAN");
        request.setAmount(new BigDecimal("20.00"));
        return request;
    }

    private ResponseEntity<Map> response(String businessChainId, String factNo,
                                         String businessNo, BigDecimal amount) {
        Map<String, Object> fact = new LinkedHashMap<String, Object>();
        fact.put("businessChainId", businessChainId);
        fact.put("systemCode", "accounting-system");
        fact.put("factType", "ACCOUNTING_POSTED");
        fact.put("factNo", factNo);
        fact.put("businessNo", businessNo);
        fact.put("payScene", "HOME_CLEAN");
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
}
