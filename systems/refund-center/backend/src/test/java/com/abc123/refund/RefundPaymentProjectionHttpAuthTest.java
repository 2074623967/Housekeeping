package com.abc123.refund;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.refund.common.PaymentProjectionHttpSignature;
import com.abc123.refund.service.RefundService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "HSP_PAYMENT_REFUND_PROJECTION_HTTP_SECRET=local-projection-test-secret",
        "spring.datasource.url=jdbc:h2:mem:refund_projection_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class RefundPaymentProjectionHttpAuthTest {

    private static final String PATH = "/api/refunds/internal/payment-success";
    private static final String BODY = "{\"paymentOrderId\":\"PAY-1\",\"orderNo\":\"ORD-1\","
            + "\"paidAmount\":88.00,\"channelCode\":\"wx_h5\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundService refundService;

    @Test
    void rejectsUnsignedProjectionBeforeWritingRefundSource() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(refundService, never()).projectPaymentSuccess(any());
    }

    @Test
    void acceptsValidSignedProjection() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, timestamp)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER,
                        PaymentProjectionHttpSignature.sign(BODY, timestamp, "local-projection-test-secret")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("0"))
                .andExpect(jsonPath("$.data.paymentOrderId").value("PAY-1"));
        verify(refundService).projectPaymentSuccess(org.mockito.ArgumentMatchers.argThat(
                projection -> "PAY-1".equals(projection.getPaymentOrderId())
                        && "ORD-1".equals(projection.getOrderNo())));
    }

    @Test
    void rejectsTamperingOldSignatureAndExpiredTimestampBeforeProjection() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String signature = PaymentProjectionHttpSignature.sign(BODY, timestamp, "local-projection-test-secret");
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY + " ")
                .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, timestamp)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER, signature))
                .andExpect(status().isForbidden());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER, signature))
                .andExpect(status().isForbidden());
        String expired = Long.toString(Instant.now().minusSeconds(3600).getEpochSecond());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, expired)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER,
                        PaymentProjectionHttpSignature.sign(BODY, expired, "local-projection-test-secret")))
                .andExpect(status().isForbidden());
        verify(refundService, never()).projectPaymentSuccess(any());
    }

    @Test
    void rejectsMalformedFutureAndDuplicateTimestampBeforeProjection() throws Exception {
        for (String invalid : new String[]{"001", "not-a-time",
                Long.toString(Instant.now().plusSeconds(3600).getEpochSecond())}) {
            mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                    .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, invalid)
                    .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER,
                            PaymentProjectionHttpSignature.sign(BODY, invalid, "local-projection-test-secret")))
                    .andExpect(status().isForbidden());
        }
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, timestamp, timestamp)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER,
                        PaymentProjectionHttpSignature.sign(BODY, timestamp, "local-projection-test-secret")))
                .andExpect(status().isForbidden());
        verify(refundService, never()).projectPaymentSuccess(any());
    }

    @Test
    void rejectsSignedInvalidJsonBeforeProjection() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String invalidBody = "{";
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(invalidBody)
                .header(PaymentProjectionHttpSignature.TIMESTAMP_HEADER, timestamp)
                .header(PaymentProjectionHttpSignature.SIGNATURE_HEADER,
                        PaymentProjectionHttpSignature.sign(invalidBody, timestamp, "local-projection-test-secret")))
                .andExpect(status().isBadRequest());
        verify(refundService, never()).projectPaymentSuccess(any());
    }
}
