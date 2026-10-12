package com.abc123.accounting.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.accounting.common.PaymentEventHttpSignature;
import com.abc123.accounting.service.AccountingEventService;
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
        "HSP_SETTLEMENT_COMPENSATION_HTTP_SECRET=local-compensation-http-test-secret",
        "spring.datasource.url=jdbc:h2:mem:accounting_compensation_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class AccountingSettlementCompensationHttpAuthTest {

    private static final String SECRET = "local-compensation-http-test-secret";
    private static final String HEADER_NAME = "X-HSP-Settlement-Compensation-Signature";
    private static final String BODY = "{\"accountNo\":\"ACT10002\",\"settlementOrderNo\":\"SLT-001\","
            + "\"bizNo\":\"CLO-001\",\"amount\":20.00,\"compensationDirection\":\"借方\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountingEventService accountingEventService;

    @Test
    void shouldRejectUnsignedCompensationBeforeBalanceChange() throws Exception {
        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeSettlementCompensation(any());
    }

    @Test
    void shouldRejectModifiedCompensationWithOriginalSignature() throws Exception {
        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, PaymentEventHttpSignature.sign(BODY, SECRET))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeSettlementCompensation(any());
    }

    @Test
    void shouldAcceptSignedCompensation() throws Exception {
        mockMvc.perform(post("/api/accounting/events/settlements/compensations")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, PaymentEventHttpSignature.sign(BODY, SECRET)).content(BODY))
                .andExpect(status().isOk());

        verify(accountingEventService).consumeSettlementCompensation(
                org.mockito.ArgumentMatchers.argThat(request -> "SLT-001".equals(request.getSettlementOrderNo())));
    }
}
