package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;

import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 清分事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingEventControllerTest {

    @Mock
    private ClearingEventService clearingEventService;

    @Test
    void shouldListClearingEvents() {
        ClearingEventController controller = new ClearingEventController(clearingEventService);

        controller.list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);

        verify(clearingEventService).list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);
    }

    @Test
    void shouldConsumePaymentSuccessEvent() {
        ClearingEventController controller = new ClearingEventController(clearingEventService);
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();

        controller.consumePaymentSuccess(request);

        verify(clearingEventService).consumePaymentSuccess(request);
    }
}
