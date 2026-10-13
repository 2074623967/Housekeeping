package com.abc123.reconciliation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.reconciliation.common.ReconciliationFactSignature;
import com.abc123.reconciliation.controller.ReconciliationController;
import com.abc123.reconciliation.dto.CrossSystemChainSnapshotDTO;
import com.abc123.reconciliation.dto.CrossSystemFactRequestDTO;
import com.abc123.reconciliation.service.ReconciliationService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class ReconciliationFactSignatureControllerTest {

    private static final String PATH = "/api/reconciliation/cross-system/facts";
    private static final String SECRET = "local-fact-signature-test-secret";
    private static final String BODY = "{\"businessChainId\":\"CHAIN-1\",\"systemCode\":\"payment-core\","
            + "\"factType\":\"PAYMENT_SUCCESS\",\"factNo\":\"PAY-1\"}";

    private ReconciliationService service;
    private ReconciliationController controller;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        service = mock(ReconciliationService.class);
        controller = new ReconciliationController(service, new ObjectMapper());
        ReflectionTestUtils.setField(controller, "paymentFactSecret", SECRET);
        mvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    @Test
    void acceptsSignedFactFromMatchingSource() throws Exception {
        when(service.recordCrossSystemFact(any())).thenReturn(new CrossSystemChainSnapshotDTO());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(ReconciliationFactSignature.SOURCE_HEADER, "payment-core")
                .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                        ReconciliationFactSignature.sign(BODY, "payment-core", timestamp, SECRET)))
                .andExpect(status().isOk());
        verify(service).recordCrossSystemFact(any(CrossSystemFactRequestDTO.class));
    }

    @Test
    void acceptsOnlyTheConfiguredKeyForEachSource() throws Exception {
        when(service.recordCrossSystemFact(any())).thenReturn(new CrossSystemChainSnapshotDTO());
        String[] sources = {"clearing-system", "settlement-system", "accounting-system", "refund-center"};
        String[] fields = {"clearingFactSecret", "settlementFactSecret", "accountingFactSecret", "refundFactSecret"};
        for (int index = 0; index < sources.length; index++) {
            String source = sources[index];
            String secret = SECRET + source;
            ReflectionTestUtils.setField(controller, fields[index], secret);
            String body = BODY.replace("payment-core", source);
            String timestamp = Long.toString(Instant.now().getEpochSecond());
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                    .header(ReconciliationFactSignature.SOURCE_HEADER, source)
                    .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                    .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                            ReconciliationFactSignature.sign(body, source, timestamp, SECRET)))
                    .andExpect(status().isUnauthorized());
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(body)
                    .header(ReconciliationFactSignature.SOURCE_HEADER, source)
                    .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                    .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                            ReconciliationFactSignature.sign(body, source, timestamp, secret)))
                    .andExpect(status().isOk());
        }
        verify(service, times(sources.length)).recordCrossSystemFact(any(CrossSystemFactRequestDTO.class));
    }

    @Test
    void rejectsMissingKeySignatureTamperingAndExpiredTimestampBeforeService() throws Exception {
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized());
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY + " ")
                .header(ReconciliationFactSignature.SOURCE_HEADER, "payment-core")
                .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                        ReconciliationFactSignature.sign(BODY, "payment-core", timestamp, SECRET)))
                .andExpect(status().isUnauthorized());
        String expired = Long.toString(Instant.now().minusSeconds(301).getEpochSecond());
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                .header(ReconciliationFactSignature.SOURCE_HEADER, "payment-core")
                .header(ReconciliationFactSignature.TIMESTAMP_HEADER, expired)
                .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                        ReconciliationFactSignature.sign(BODY, "payment-core", expired, SECRET)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }

    @Test
    void rejectsUnknownSourceUnconfiguredSourceAndBodySourceMismatch() throws Exception {
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        for (String source : new String[]{"unknown", "clearing-system"}) {
            mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON).content(BODY)
                    .header(ReconciliationFactSignature.SOURCE_HEADER, source)
                    .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                    .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                            ReconciliationFactSignature.sign(BODY, source, timestamp, SECRET)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(post(PATH).contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("payment-core", "clearing-system"))
                .header(ReconciliationFactSignature.SOURCE_HEADER, "payment-core")
                .header(ReconciliationFactSignature.TIMESTAMP_HEADER, timestamp)
                .header(ReconciliationFactSignature.SIGNATURE_HEADER,
                        ReconciliationFactSignature.sign(BODY.replace("payment-core", "clearing-system"),
                                "payment-core", timestamp, SECRET)))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(service);
    }
}
