package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;

import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 账务事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class AccountingEventControllerTest {

    @Mock
    private AccountingEventService accountingEventService;

    @Test
    void shouldListAccountingEvents() {
        AccountingEventController controller = new AccountingEventController(accountingEventService);

        controller.list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);

        verify(accountingEventService).list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);
    }

    @Test
    void shouldConsumePaymentSuccessEvent() {
        AccountingEventController controller = new AccountingEventController(accountingEventService);
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();

        controller.consumePaymentSuccess(request);

        verify(accountingEventService).consumePaymentSuccess(request);
    }

    @Test
    void shouldConsumeClearingGeneratedEvent() {
        AccountingEventController controller = new AccountingEventController(accountingEventService);
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();

        controller.consumeClearingGenerated(request);

        verify(accountingEventService).consumeClearingGenerated(request);
    }
}
