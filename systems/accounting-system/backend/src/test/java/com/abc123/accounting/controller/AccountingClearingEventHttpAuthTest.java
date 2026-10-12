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
        "HSP_CLEARING_GENERATED_HTTP_SECRET=local-accounting-clearing-test-secret",
        "spring.datasource.url=jdbc:h2:mem:accounting_clearing_http_auth;MODE=MySQL;DATABASE_TO_LOWER=TRUE"
})
class AccountingClearingEventHttpAuthTest {

    private static final String SECRET = "local-accounting-clearing-test-secret";
    private static final String HEADER_NAME = "X-HSP-Clearing-Event-Signature";
    private static final String BODY = "{\"clearingOrderNo\":\"CLO-001\",\"businessChainId\":\"CHAIN-001\","
            + "\"accountNo\":\"ACT10002\",\"amount\":20.00}";

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AccountingEventService accountingEventService;

    @Test
    void shouldRejectUnsignedClearingBeforeCrediting() throws Exception {
        mockMvc.perform(post("/api/accounting/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeClearingGenerated(any());
    }

    @Test
    void shouldRejectModifiedBodyWithOriginalSignature() throws Exception {
        mockMvc.perform(post("/api/accounting/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, PaymentEventHttpSignature.sign(BODY, SECRET))
                .content(BODY.replace("20.00", "200.00")))
                .andExpect(status().isForbidden());

        verify(accountingEventService, never()).consumeClearingGenerated(any());
    }

    @Test
    void shouldAcceptSignedClearingEvent() throws Exception {
        mockMvc.perform(post("/api/accounting/events/clearing/generated")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HEADER_NAME, PaymentEventHttpSignature.sign(BODY, SECRET)).content(BODY))
                .andExpect(status().isOk());

        verify(accountingEventService).consumeClearingGenerated(
                org.mockito.ArgumentMatchers.argThat(request -> "CLO-001".equals(request.getClearingOrderNo())));
    }
}
