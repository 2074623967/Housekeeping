package com.abc123.clearing.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.argThat;

import com.abc123.clearing.common.PaymentEventHttpSignature;
import com.abc123.clearing.dto.PaymentSuccessEventRequestDTO;
import com.abc123.clearing.service.ClearingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
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

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        controller.consumePaymentSuccess(body, PaymentEventHttpSignature.signTimed(body, timestamp, TEST_SECRET), timestamp);

        verify(clearingEventService).consumePaymentSuccess(argThat(
                received -> "PAY-001".equals(received.getPaymentOrderId())));
    }

    @Test
    void shouldReplayDueFailedEvents() {
        ClearingEventController controller = controller();
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        controller.replayDueFailedEvents(PaymentEventHttpSignature.signTimed(
                "POST /api/clearing/events/replay-failed", timestamp, TEST_SECRET), timestamp);

        verify(clearingEventService).replayDueFailedEvents();
    }

    @Test
    void shouldRejectReplayWhenSecretIsNotConfigured() {
        ClearingEventController controller = new ClearingEventController(clearingEventService, new ObjectMapper(),
                TEST_SECRET, "");
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        assertEquals(403, controller.replayDueFailedEvents(PaymentEventHttpSignature.signTimed(
                "POST /api/clearing/events/replay-failed", timestamp, TEST_SECRET), timestamp).getStatusCodeValue());
        verify(clearingEventService, never()).replayDueFailedEvents();
    }

    private ClearingEventController controller() {
        return new ClearingEventController(clearingEventService, new ObjectMapper(), TEST_SECRET, TEST_SECRET);
    }
}
