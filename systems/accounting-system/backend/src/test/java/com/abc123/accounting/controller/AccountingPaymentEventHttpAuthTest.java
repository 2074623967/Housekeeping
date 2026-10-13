package com.abc123.accounting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.accounting.service.AccountingEventService;
import com.abc123.accounting.common.PaymentEventHttpSignature;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "HSP_PAYMENT_SUCCESS_HTTP_SECRET=local-accounting-http-test-secret")
class AccountingPaymentEventHttpAuthTest {

    private static final String SECRET = "local-accounting-http-test-secret";
    private static final String BODY = "{\"paymentOrderId\":\"PAY-001\",\"orderNo\":\"ORD-001\",\"accountNo\":\"ACT10001\",\"amount\":20.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountingEventService accountingEventService;

    @Test
    void shouldRejectUnsignedPaymentSuccessBeforeCrediting() throws Exception {
        mockMvc.perform(post("/api/accounting/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumePaymentSuccess(any());
    }

    @Test
    void shouldRejectModifiedBodyWithOriginalSignature() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/accounting/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumePaymentSuccess(any());
    }

    @Test
    void shouldAcceptSignedPaymentSuccess() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/accounting/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isOk());

        verify(accountingEventService).consumePaymentSuccess(
                org.mockito.ArgumentMatchers.argThat(request -> "PAY-001".equals(request.getPaymentOrderId())));
    }

    @Test
    void shouldRejectLegacyExpiredFutureAndChangedTimestampsBeforeCrediting() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String expired = Long.toString(Instant.now().getEpochSecond() - 3600);
        String future = Long.toString(Instant.now().getEpochSecond() + 3600);
        rejectPaymentEvent(timestamp, PaymentEventHttpSignature.sign(BODY, SECRET));
        rejectPaymentEvent(expired, PaymentEventHttpSignature.signTimed(BODY, expired, SECRET));
        rejectPaymentEvent(future, PaymentEventHttpSignature.signTimed(BODY, future, SECRET));
        rejectPaymentEvent(Long.toString(Long.parseLong(timestamp) + 1),
                PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET));
        rejectPaymentEvent("0" + timestamp, PaymentEventHttpSignature.signTimed(BODY, "0" + timestamp, SECRET));
        rejectPaymentEvent("not-a-time", PaymentEventHttpSignature.signTimed(BODY, "not-a-time", SECRET));
        mockMvc.perform(post("/api/accounting/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp, "0")
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumePaymentSuccess(any());
    }

    private void rejectPaymentEvent(String timestamp, String signature) throws Exception {
        mockMvc.perform(post("/api/accounting/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, signature)
                .content(BODY))
                .andExpect(status().isForbidden());
    }
}
