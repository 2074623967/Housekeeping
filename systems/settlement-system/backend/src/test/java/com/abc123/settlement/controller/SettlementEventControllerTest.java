package com.abc123.settlement.controller;

import static org.mockito.Mockito.verify;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.service.SettlementEventService;
import com.abc123.settlement.common.ClearingEventHttpSignature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 结算事件控制器测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementEventControllerTest {

    @Mock
    private SettlementEventService settlementEventService;

    @Test
    void shouldListSettlementEvents() {
        SettlementEventController controller = new SettlementEventController(
                settlementEventService, new ObjectMapper(), "local-test-secret");

        controller.list("CLEARING_GENERATED", "CLO20001", 1, 20);

        verify(settlementEventService).list("CLEARING_GENERATED", "CLO20001", 1, 20);
    }

    @Test
    void shouldConsumeClearingGeneratedEvent() throws Exception {
        SettlementEventController controller = new SettlementEventController(
                settlementEventService, new ObjectMapper(), "local-test-secret");
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        String body = new ObjectMapper().writeValueAsString(request);

        controller.consumeClearingGenerated(body, ClearingEventHttpSignature.sign(body, "local-test-secret"));

        verify(settlementEventService).consumeClearingGenerated(
                org.mockito.ArgumentMatchers.argThat(received -> received != null));
    }
}
