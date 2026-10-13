package com.abc123.refund;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.refund.common.RefundCallbackHttpSignature;
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
        "HSP_REFUND_CALLBACK_HTTP_SECRET=local-refund-callback-test-secret",
        "spring.datasource.url=jdbc:h2:mem:refund_callback_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class RefundCallbackHttpAuthTest {

    private static final String SECRET = "local-refund-callback-test-secret";
    private static final String REFUND_ORDER_ID = "REF-1";
    private static final String PATH = "/api/refunds/" + REFUND_ORDER_ID + "/callback";
    private static final String BODY = "{\"result\":\"SUCCESS\",\"channelRefundId\":\"CH-1\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefundService refundService;

    @Test
    void rejectsUnsignedCallbackBeforeChangingRefund() throws Exception {
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        verify(refundService, never()).callback(any());
    }

    @Test
    void acceptsSignedCallbackAndIdenticalRetry() throws Exception {
        String timestamp = now();
        mockMvc.perform(signed(PATH, REFUND_ORDER_ID, BODY, timestamp)).andExpect(status().isOk());
        mockMvc.perform(signed(PATH, REFUND_ORDER_ID, BODY, timestamp)).andExpect(status().isOk());
        verify(refundService, times(2)).callback(org.mockito.ArgumentMatchers.argThat(
                request -> REFUND_ORDER_ID.equals(request.getRefundOrderId())
                        && "CH-1".equals(request.getChannelRefundId())));
    }

    @Test
    void rejectsBodyOrPathTamperingBeforeChangingRefund() throws Exception {
        String timestamp = now();
        mockMvc.perform(signed(PATH, REFUND_ORDER_ID, BODY, timestamp).content(BODY + " "))
                .andExpect(status().isForbidden());
        mockMvc.perform(signed("/api/refunds/REF-2/callback", REFUND_ORDER_ID, BODY, timestamp))
                .andExpect(status().isForbidden());
        verify(refundService, never()).callback(any());
    }

    @Test
    void rejectsMissingMalformedExpiredAndFutureTimestamp() throws Exception {
        String timestamp = now();
        mockMvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(RefundCallbackHttpSignature.SIGNATURE_HEADER,
                        RefundCallbackHttpSignature.sign(REFUND_ORDER_ID, BODY, timestamp, SECRET)))
                .andExpect(status().isForbidden());
        for (String invalid : new String[]{"001", "not-a-time",
                Long.toString(Instant.now().minusSeconds(3600).getEpochSecond()),
                Long.toString(Instant.now().plusSeconds(3600).getEpochSecond())}) {
            mockMvc.perform(signed(PATH, REFUND_ORDER_ID, BODY, invalid))
                    .andExpect(status().isForbidden());
        }
        mockMvc.perform(signed(PATH, REFUND_ORDER_ID, BODY, timestamp)
                .header(RefundCallbackHttpSignature.TIMESTAMP_HEADER, timestamp))
                .andExpect(status().isForbidden());
        verify(refundService, never()).callback(any());
    }

    @Test
    void rejectsInvalidJsonOnlyAfterSignatureValidation() throws Exception {
        mockMvc.perform(signed(PATH, REFUND_ORDER_ID, "{", now()))
                .andExpect(status().isBadRequest());
        verify(refundService, never()).callback(any());
    }

    @Test
    void rejectsUnconfiguredSecret() {
        org.junit.jupiter.api.Assertions.assertFalse(RefundCallbackHttpSignature.matches(
                REFUND_ORDER_ID, BODY, now(), "signature", " "));
    }

    private String now() {
        return Long.toString(Instant.now().getEpochSecond());
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder signed(
            String path, String signedRefundOrderId, String body, String timestamp) {
        return post(path).contentType(MediaType.APPLICATION_JSON).content(body)
                .header(RefundCallbackHttpSignature.TIMESTAMP_HEADER, timestamp)
                .header(RefundCallbackHttpSignature.SIGNATURE_HEADER,
                        RefundCallbackHttpSignature.sign(signedRefundOrderId, body, timestamp, SECRET));
    }
}
