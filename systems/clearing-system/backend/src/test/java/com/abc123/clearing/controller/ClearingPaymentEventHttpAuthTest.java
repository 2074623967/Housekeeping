package com.abc123.clearing.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.clearing.service.ClearingEventService;
import com.abc123.clearing.common.PaymentEventHttpSignature;
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
@TestPropertySource(properties = {
        "HSP_PAYMENT_SUCCESS_HTTP_SECRET=local-clearing-http-test-secret",
        "HSP_CLEARING_REPLAY_HTTP_SECRET=local-clearing-replay-test-secret",
        "spring.datasource.url=jdbc:h2:mem:clearing_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class ClearingPaymentEventHttpAuthTest {

    private static final String SECRET = "local-clearing-http-test-secret";
    private static final String REPLAY_SECRET = "local-clearing-replay-test-secret";
    private static final String REPLAY_PAYLOAD = "POST /api/clearing/events/replay-failed";
    private static final String BODY = "{\"paymentOrderId\":\"PAY-001\",\"orderNo\":\"ORD-001\",\"amount\":20.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ClearingEventService clearingEventService;

    @Test
    void shouldRejectUnsignedPaymentSuccessBeforeClearing() throws Exception {
        mockMvc.perform(post("/api/clearing/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(clearingEventService, never()).consumePaymentSuccess(any());
    }

    @Test
    void shouldRejectModifiedBodyWithOriginalSignature() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/clearing/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(clearingEventService, never()).consumePaymentSuccess(any());
    }

    @Test
    void shouldAcceptSignedPaymentSuccess() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/clearing/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isOk());

        verify(clearingEventService).consumePaymentSuccess(
                org.mockito.ArgumentMatchers.argThat(request -> "PAY-001".equals(request.getPaymentOrderId())));
    }

    @Test
    void shouldRejectLegacyExpiredFutureAndChangedTimestampsBeforeClearing() throws Exception {
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
        mockMvc.perform(post("/api/clearing/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp, "0")
                .header(PaymentEventHttpSignature.HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(clearingEventService, never()).consumePaymentSuccess(any());
    }

    private void rejectPaymentEvent(String timestamp, String signature) throws Exception {
        mockMvc.perform(post("/api/clearing/events/payments/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(PaymentEventHttpSignature.TIMESTAMP_HEADER_NAME, timestamp)
                .header(PaymentEventHttpSignature.HEADER_NAME, signature)
                .content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectUnsignedAndWronglySignedReplayBeforeDispatch() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/clearing/events/replay-failed"))
                .andExpect(status().isForbidden());
        rejectReplay(timestamp, PaymentEventHttpSignature.signTimed(REPLAY_PAYLOAD, timestamp, SECRET));
        rejectReplay(timestamp, PaymentEventHttpSignature.signTimed("POST /api/clearing/events/payments/success",
                timestamp, REPLAY_SECRET));
        verify(clearingEventService, never()).replayDueFailedEvents();
    }

    @Test
    void shouldRejectStaleFutureAndModifiedReplayTimestamps() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String expired = Long.toString(Instant.now().getEpochSecond() - 3600);
        String future = Long.toString(Instant.now().getEpochSecond() + 3600);
        rejectReplay(expired, PaymentEventHttpSignature.signTimed(REPLAY_PAYLOAD, expired, REPLAY_SECRET));
        rejectReplay(future, PaymentEventHttpSignature.signTimed(REPLAY_PAYLOAD, future, REPLAY_SECRET));
        rejectReplay(Long.toString(Long.parseLong(timestamp) + 1),
                PaymentEventHttpSignature.signTimed(REPLAY_PAYLOAD, timestamp, REPLAY_SECRET));
        verify(clearingEventService, never()).replayDueFailedEvents();
    }

    @Test
    void shouldAcceptSignedReplay() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/clearing/events/replay-failed")
                .header("X-HSP-Clearing-Replay-Timestamp", timestamp)
                .header("X-HSP-Clearing-Replay-Signature",
                        PaymentEventHttpSignature.signTimed(REPLAY_PAYLOAD, timestamp, REPLAY_SECRET)))
                .andExpect(status().isOk());
        verify(clearingEventService).replayDueFailedEvents();
    }

    private void rejectReplay(String timestamp, String signature) throws Exception {
        mockMvc.perform(post("/api/clearing/events/replay-failed")
                .header("X-HSP-Clearing-Replay-Timestamp", timestamp)
                .header("X-HSP-Clearing-Replay-Signature", signature))
                .andExpect(status().isForbidden());
    }
}
