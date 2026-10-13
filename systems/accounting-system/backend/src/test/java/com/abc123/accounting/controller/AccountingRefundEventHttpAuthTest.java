package com.abc123.accounting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.service.AccountingEventService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "HSP_REFUND_SUCCESS_HTTP_SECRET=local-refund-http-test-secret",
        "spring.datasource.url=jdbc:h2:mem:accounting_refund_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class AccountingRefundEventHttpAuthTest {

    private static final String SECRET = "local-refund-http-test-secret";
    private static final String HEADER_NAME = "X-HSP-Refund-Event-Signature";
    private static final String TIMESTAMP_HEADER_NAME = "X-HSP-Refund-Event-Timestamp";
    private static final String BODY = "{\"businessChainId\":\"CHAIN-001\",\"accountNo\":\"ACT10003\","
            + "\"refundOrderId\":\"REF-001\",\"paymentOrderId\":\"PAY-001\",\"amount\":20.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountingEventService accountingEventService;

    @Test
    void shouldRejectUnsignedRefundBeforeDebiting() throws Exception {
        mockMvc.perform(post("/api/accounting/events/refunds/success")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeRefundSuccess(any());
    }

    @Test
    void shouldRejectModifiedRefundBodyWithOriginalSignature() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/accounting/events/refunds/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER_NAME, timestamp)
                .header(HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeRefundSuccess(any());
    }

    @Test
    void shouldAcceptSignedRefundSuccess() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post("/api/accounting/events/refunds/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER_NAME, timestamp)
                .header(HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isOk());

        verify(accountingEventService).consumeRefundSuccess(
                org.mockito.ArgumentMatchers.argThat(request -> "REF-001".equals(request.getRefundOrderId())));
    }

    @Test
    void shouldRejectLegacyExpiredFutureAndChangedTimestampsBeforeDebiting() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String expired = Long.toString(Instant.now().getEpochSecond() - 3600);
        String future = Long.toString(Instant.now().getEpochSecond() + 3600);
        rejectRefundEvent(timestamp, PaymentEventHttpSignature.sign(BODY, SECRET));
        rejectRefundEvent(expired, PaymentEventHttpSignature.signTimed(BODY, expired, SECRET));
        rejectRefundEvent(future, PaymentEventHttpSignature.signTimed(BODY, future, SECRET));
        rejectRefundEvent(Long.toString(Long.parseLong(timestamp) + 1),
                PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET));
        rejectRefundEvent("0" + timestamp, PaymentEventHttpSignature.signTimed(BODY, "0" + timestamp, SECRET));
        rejectRefundEvent("not-a-time", PaymentEventHttpSignature.signTimed(BODY, "not-a-time", SECRET));
        mockMvc.perform(post("/api/accounting/events/refunds/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER_NAME, timestamp, "0")
                .header(HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeRefundSuccess(any());
    }

    private void rejectRefundEvent(String timestamp, String signature) throws Exception {
        mockMvc.perform(post("/api/accounting/events/refunds/success")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER_NAME, timestamp)
                .header(HEADER_NAME, signature)
                .content(BODY))
                .andExpect(status().isForbidden());
    }
}
