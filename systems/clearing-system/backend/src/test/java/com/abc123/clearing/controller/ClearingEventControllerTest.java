package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.argThat;

import com.abc123.clearing.common.PaymentEventHttpSignature;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 清分事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class ClearingEventControllerTest {

    private static final String TEST_SECRET = "local-clearing-test-secret";

    @Mock
    private ClearingEventService clearingEventService;

    @Test
    void shouldListClearingEvents() {
        ClearingEventController controller = controller();

        controller.list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);

        verify(clearingEventService).list("PAYMENT_SUCCESS", "PAY202608030001", 1, 20);
    }

    @Test
    void shouldConsumePaymentSuccessEvent() throws Exception {
        ClearingEventController controller = controller();
        PaymentSuccessEventRequestDTO request = new PaymentSuccessEventRequestDTO();
        request.setPaymentOrderId("PAY-001");
        String body = new ObjectMapper().writeValueAsString(request);

        controller.consumePaymentSuccess(body, PaymentEventHttpSignature.sign(body, TEST_SECRET));

        verify(clearingEventService).consumePaymentSuccess(argThat(
                received -> "PAY-001".equals(received.getPaymentOrderId())));
    }

    @Test
    void shouldReplayDueFailedEvents() {
        ClearingEventController controller = controller();

        controller.replayDueFailedEvents();

        verify(clearingEventService).replayDueFailedEvents();
    }

    private ClearingEventController controller() {
        return new ClearingEventController(clearingEventService, new ObjectMapper(), TEST_SECRET);
    }
}
