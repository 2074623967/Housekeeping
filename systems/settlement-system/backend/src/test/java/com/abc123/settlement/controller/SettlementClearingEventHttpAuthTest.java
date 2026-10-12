package com.abc123.settlement.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.settlement.service.SettlementEventService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
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
        "HSP_CLEARING_GENERATED_HTTP_SECRET=local-settlement-clearing-test-secret",
        "spring.datasource.url=jdbc:h2:mem:settlement_clearing_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class SettlementClearingEventHttpAuthTest {

    private static final String SECRET = "local-settlement-clearing-test-secret";
    private static final String HEADER_NAME = "X-HSP-Clearing-Event-Signature";
    private static final String BODY = "{\"clearingNo\":\"CLO-001\",\"paymentOrderId\":\"PAY-001\","
            + "\"shouldSettleAmount\":20.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private SettlementEventService settlementEventService;

    @Test
    void shouldRejectUnsignedClearingBeforeSettlement() throws Exception {
        mockMvc.perform(post("/api/settlements/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(settlementEventService, never()).consumeClearingGenerated(any());
    }

    @Test
    void shouldRejectModifiedBodyWithOriginalSignature() throws Exception {
        mockMvc.perform(post("/api/settlements/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, sign(BODY))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(settlementEventService, never()).consumeClearingGenerated(any());
    }

    @Test
    void shouldAcceptSignedClearingEvent() throws Exception {
        mockMvc.perform(post("/api/settlements/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, sign(BODY)).content(BODY))
                .andExpect(status().isOk());

        verify(settlementEventService).consumeClearingGenerated(
                org.mockito.ArgumentMatchers.argThat(request -> "CLO-001".equals(request.getClearingNo())));
    }

    private String sign(String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return Base64.getEncoder().encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }
}
