package com.abc123.accounting.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.service.BalanceService;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "HSP_SETTLEMENT_COMPENSATION_HTTP_SECRET=local-compensation-integration-secret",
        "spring.datasource.url=jdbc:h2:mem:accounting_compensation_http_integration;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class AccountingSettlementCompensationHttpIntegrationTest {

    private static final String SECRET = "local-compensation-integration-secret";
    private static final String HEADER_NAME = "X-HSP-Settlement-Compensation-Signature";
    private static final String TIMESTAMP_HEADER = "X-HSP-Settlement-Compensation-Timestamp";
    private static final String BODY = "{\"accountNo\":\"ACT10002\",\"settlementOrderNo\":\"SLT-HTTP-001\","
            + "\"bizNo\":\"CLO-HTTP-001\",\"amount\":15.00,\"compensationDirection\":\"贷方\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BalanceService balanceService;

    @Test
    void shouldOnlyCreditSignedCompensationAndKeepReplayIdempotent() throws Exception {
        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());
        String timestamp = Long.toString(Instant.now().getEpochSecond());

        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());

        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(HEADER_NAME, PaymentEventHttpSignature.sign(BODY, SECRET))
                .content(BODY))
                .andExpect(status().isForbidden());
        assertEquals("¥120.00", balanceService.detail("ACT10002").getAvailableAmount());

        for (int attempt = 0; attempt < 2; attempt++) {
            mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header(TIMESTAMP_HEADER, timestamp)
                    .header(HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET)).content(BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"));
            assertEquals("¥135.00", balanceService.detail("ACT10002").getAvailableAmount());
        }

        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(TIMESTAMP_HEADER, timestamp)
                .header(HEADER_NAME, PaymentEventHttpSignature.signTimed(BODY, timestamp, SECRET))
                .content(BODY.replace("15.00", "150.00")))
                .andExpect(status().isForbidden());
        assertEquals("¥135.00", balanceService.detail("ACT10002").getAvailableAmount());
    }
}
