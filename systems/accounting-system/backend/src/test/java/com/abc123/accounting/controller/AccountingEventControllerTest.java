package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.dto.SettlementCompensationEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账务事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class AccountingEventControllerTest {

    private static final String TEST_SECRET = "local-accounting-test-secret";

    @Mock
    private AccountingEventService accountingEventService;

    @Test
    void shouldListAccountingEvents() {
        AccountingEventController controller = controller();

        controller.list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);

        verify(accountingEventService).list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);
    }

    @Test
    void shouldConsumePaymentSuccessEvent() throws Exception {
        AccountingEventController controller = controller();
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY-001");
        String body = new ObjectMapper().writeValueAsString(request);

        controller.consumePaymentSuccess(body, PaymentEventHttpSignature.sign(body, TEST_SECRET));

        verify(accountingEventService).consumePaymentSuccess(argThat(
                received -> "PAY-001".equals(received.getPaymentOrderId())));
    }

    @Test
    void shouldConsumeClearingGeneratedEvent() {
        AccountingEventController controller = controller();
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();

        controller.consumeClearingGenerated(request);

        verify(accountingEventService).consumeClearingGenerated(request);
    }

    @Test
    void shouldConsumeRefundSuccessEvent() {
        AccountingEventController controller = controller();
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();

        controller.consumeRefundSuccess(request);

        verify(accountingEventService).consumeRefundSuccess(request);
    }

    @Test
    void shouldConsumeSettlementCompensationEvent() {
        AccountingEventController controller = controller();
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();

        controller.consumeSettlementCompensation(request);

        verify(accountingEventService).consumeSettlementCompensation(request);
    }

    private AccountingEventController controller() {
        return new AccountingEventController(accountingEventService, new ObjectMapper(), TEST_SECRET);
    }
}
