package com.abc123.hsp.service.impl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentDetailDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentMapper;
import java.lang.reflect.Method;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

/**
 * 支付事件下游投递服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventDispatchServiceImplTest {

    @Mock
    private PaymentMapper paymentMapper;
    @Mock
    private PaymentEventMapper paymentEventMapper;
    @Mock
    private RestTemplate restTemplate;

    @Test
    void shouldPublishPaymentSuccessToClearingAndAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        verify(restTemplate).postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class));
        verify(restTemplate).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class));
        verify(paymentEventMapper).markPublishSuccess("EVT-001");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-001");
    }

    @Test
    void shouldMarkFailedWhenAccountingReturnsNon2xx() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-001");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setRetryCount(0);
        when(paymentEventMapper.findByEventNo("EVT-001")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("fail", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-001");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishFailed("EVT-001");
    }

    @Test
    void shouldMarkDeadLetterWhenRetryCountReachesUpperLimit() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-RETRY-003");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setRetryCount(2);
        when(paymentEventMapper.findByEventNo("EVT-RETRY-003")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("fail", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                3,
                restTemplate
        ).republish("EVT-RETRY-003");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishDeadLetter("EVT-RETRY-003");
        verify(paymentEventMapper, never()).markPublishFailed("EVT-RETRY-003");
    }

    @Test
    void shouldReturnFalseWhenRepublishEventMissing() {
        when(paymentEventMapper.findByEventNo("EVT-MISSING")).thenReturn(null);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-MISSING");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findDetail(any());
    }

    @Test
    void shouldReturnFalseWhenRepublishEventTypeUnsupported() {
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-REFUND-001");
        event.setEventType("REFUND_SUCCESS");
        when(paymentEventMapper.findByEventNo("EVT-REFUND-001")).thenReturn(event);

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-REFUND-001");

        Assertions.assertFalse(success);
        verify(paymentMapper, never()).findDetail(any());
        verify(paymentEventMapper, never()).markPublishFailed(any());
        verify(paymentEventMapper, never()).markPublishDeadLetter(any());
    }

    @Test
    void shouldMarkFailedWhenPaymentDetailMissing() {
        when(paymentMapper.findDetail("PAY-404")).thenReturn(null);

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-404", "PAY-404");

        verify(paymentEventMapper).markPublishFailed("EVT-404");
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class));
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class));
    }

    @Test
    void shouldStopAtClearingFailureWithoutPostingAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        PaymentEventListItemDTO event = new PaymentEventListItemDTO();
        event.setEventNo("EVT-CLEARING-FAIL");
        event.setEventType("PAYMENT_SUCCESS");
        event.setPaymentOrderId("PAY-001");
        event.setRetryCount(0);
        when(paymentEventMapper.findByEventNo("EVT-CLEARING-FAIL")).thenReturn(event);
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("fail", HttpStatus.BAD_GATEWAY));

        boolean success = new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).republish("EVT-CLEARING-FAIL");

        Assertions.assertFalse(success);
        verify(paymentEventMapper).markPublishFailed("EVT-CLEARING-FAIL");
        verify(restTemplate, never()).postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class));
    }

    @Test
    void shouldBuildExpectedPayloadForClearingAndAccounting() {
        PaymentDetailDTO detail = buildPaymentDetail();
        when(paymentMapper.findDetail("PAY-001")).thenReturn(detail);
        when(paymentMapper.findWorkerNameByOrderNo("ORD-001")).thenReturn("李阿姨");
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18120/api/clearing/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));
        when(restTemplate.postForEntity(eq("http://127.0.0.1:18110/api/accounting/events/payments/success"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<String>("ok", HttpStatus.OK));

        new PaymentEventDispatchServiceImpl(
                paymentMapper,
                paymentEventMapper,
                "http://127.0.0.1:18120/api/clearing/events/payments/success",
                "http://127.0.0.1:18110/api/accounting/events/payments/success",
                "ACT10003",
                restTemplate
        ).publishPaymentSuccess("EVT-001", "PAY-001");

        ArgumentCaptor<Object> clearingCaptor = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<Object> accountingCaptor = ArgumentCaptor.forClass(Object.class);
        verify(restTemplate).postForEntity(
                eq("http://127.0.0.1:18120/api/clearing/events/payments/success"),
                clearingCaptor.capture(),
                eq(String.class)
        );
        verify(restTemplate).postForEntity(
                eq("http://127.0.0.1:18110/api/accounting/events/payments/success"),
                accountingCaptor.capture(),
                eq(String.class)
        );
        Assertions.assertEquals("PAY-001", invokeGetter(clearingCaptor.getValue(), "getPaymentOrderId"));
        Assertions.assertEquals("李阿姨", invokeGetter(clearingCaptor.getValue(), "getWorkerName"));
        Assertions.assertEquals("ACT10003", invokeGetter(accountingCaptor.getValue(), "getAccountNo"));
        Assertions.assertEquals("PAY-001", invokeGetter(accountingCaptor.getValue(), "getPaymentOrderId"));
    }

    private PaymentDetailDTO buildPaymentDetail() {
        PaymentDetailDTO detail = new PaymentDetailDTO();
        detail.setPaymentOrderId("PAY-001");
        detail.setOrderNo("ORD-001");
        detail.setCustomerName("张女士");
        detail.setAmount("¥168.00");
        return detail;
    }

    private Object invokeGetter(Object target, String methodName) {
        try {
            Method method = target.getClass().getMethod(methodName);
            method.setAccessible(true);
            return method.invoke(target);
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
