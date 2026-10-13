package com.abc123.accounting.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.accounting.dto.PaymentSuccessEventRequestDTO;
import com.abc123.accounting.dto.RefundSuccessEventRequestDTO;
import com.abc123.accounting.dto.SettlementCompensationEventRequestDTO;
import com.abc123.accounting.service.AccountingEventService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

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

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        controller.consumePaymentSuccess(body, PaymentEventHttpSignature.signTimed(body, timestamp, TEST_SECRET), timestamp);

        verify(accountingEventService).consumePaymentSuccess(argThat(
                received -> "PAY-001".equals(received.getPaymentOrderId())));
    }

    @Test
    void shouldConsumeClearingGeneratedEvent() throws Exception {
        AccountingEventController controller = controller();
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        String body = new ObjectMapper().writeValueAsString(request);

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        controller.consumeClearingGenerated(body, PaymentEventHttpSignature.signTimed(body, timestamp, TEST_SECRET), timestamp);

        verify(accountingEventService).consumeClearingGenerated(argThat(received -> received != null));
    }

    @Test
    void shouldConsumeRefundSuccessEvent() throws Exception {
        AccountingEventController controller = controller();
        RefundSuccessEventRequestDTO request = new RefundSuccessEventRequestDTO();
        String body = new ObjectMapper().writeValueAsString(request);

        String timestamp = Long.toString(Instant.now().getEpochSecond());
        controller.consumeRefundSuccess(body, PaymentEventHttpSignature.signTimed(body, timestamp, TEST_SECRET), timestamp);

        verify(accountingEventService).consumeRefundSuccess(argThat(received -> received != null));
    }

    @Test
    void shouldConsumeSettlementCompensationEvent() throws Exception {
        AccountingEventController controller = controller();
        SettlementCompensationEventRequestDTO request = new SettlementCompensationEventRequestDTO();
        String body = new ObjectMapper().writeValueAsString(request);

        controller.consumeSettlementCompensation(body, PaymentEventHttpSignature.sign(body, TEST_SECRET));

        verify(accountingEventService).consumeSettlementCompensation(argThat(received -> received != null));
    }

    @Test
    void shouldRejectCompensationWhenSecretIsNotConfigured() {
        AccountingEventController controller = new AccountingEventController(accountingEventService,
                new ObjectMapper(), TEST_SECRET, TEST_SECRET, TEST_SECRET, "");

        assertEquals(HttpStatus.FORBIDDEN, controller.consumeSettlementCompensation(
                "{}", PaymentEventHttpSignature.sign("{}", TEST_SECRET)).getStatusCode());

        verify(accountingEventService, never()).consumeSettlementCompensation(any());
    }

    private AccountingEventController controller() {
        return new AccountingEventController(accountingEventService, new ObjectMapper(),
                TEST_SECRET, TEST_SECRET, TEST_SECRET, TEST_SECRET);
    }
}
