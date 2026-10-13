package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 结算事件服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SettlementEventServiceImplTest {

    @Autowired
    private SettlementEventServiceImpl settlementEventService;

    @Autowired
    private SettlementBatchServiceImpl settlementBatchService;

    @Autowired
    private SettlementOrderServiceImpl settlementOrderService;

    @Test
    void shouldGenerateSettlementOrderWhenClearingEventConsumed() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO88888");
        request.setPaymentOrderId("PAY88888");
        request.setPayScene("HOME_CLEAN");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK88888");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));

        SettlementEventDTO result = settlementEventService.consumeClearingGenerated(request);
        PageResultDTO<SettlementOrderDTO> orderResult = settlementOrderService.list("", "", "", "CLO88888", 1, 20);

        assertEquals("CLEARING_GENERATED", result.getEventType());
        assertEquals("已消费", result.getEventStatus());
        assertEquals("success", result.getStatusType());
        assertEquals(2, settlementBatchService.list("", "", 1, 20).getTotal());
        assertEquals(1, orderResult.getTotal());
        assertEquals("CLO88888", orderResult.getItems().get(0).getClearingNo());
        assertEquals("CHAIN-PAY88888", orderResult.getItems().get(0).getBusinessChainId());
        assertEquals("HOME_CLEAN", orderResult.getItems().get(0).getPayScene());
        assertEquals("{\"clearingNo\":\"CLO88888\",\"payScene\":\"HOME_CLEAN\"}", result.getPayload());
    }

    @Test
    void shouldKeepClearingEventPayloadValidForSpecialCharacters() throws Exception {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO-\"QUOTED\\NO");
        request.setPaymentOrderId("PAY-QUOTED-NO");
        request.setPayScene("HOME_\"CLEAN\nNEXT");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-QUOTED-NO");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));

        SettlementEventDTO first = settlementEventService.consumeClearingGenerated(request);
        SettlementEventDTO replay = settlementEventService.consumeClearingGenerated(request);

        assertEquals(first.getEventNo(), replay.getEventNo());
        JsonNode payload = new ObjectMapper().readTree(first.getPayload());
        assertEquals(request.getClearingNo(), payload.get("clearingNo").asText());
        assertEquals(request.getPayScene(), payload.get("payScene").asText());
        assertEquals(1, settlementEventService.list("CLEARING_GENERATED", request.getClearingNo(), 1, 20).getTotal());
    }

    @Test
    void shouldRejectClearingEventWithMismatchedBusinessChain() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO77777");
        request.setPaymentOrderId("PAY77777");
        request.setBusinessChainId("CHAIN-OTHER");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK77777");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));

        assertThrows(IllegalArgumentException.class,
                () -> settlementEventService.consumeClearingGenerated(request));
    }

    @Test
    void shouldRejectClearingEventWithOverPrecisionAmountBeforePersistence() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO-SCALE");
        request.setPaymentOrderId("PAY-SCALE");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-SCALE");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.001"));
        request.setDeductAmount(BigDecimal.ZERO);

        assertThrows(IllegalArgumentException.class,
                () -> settlementEventService.consumeClearingGenerated(request));
    }

    @Test
    void shouldRejectClearingEventWithMismatchedNetAmountBeforePersistence() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO-NET-MISMATCH");
        request.setPaymentOrderId("PAY-NET-MISMATCH");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-NET-MISMATCH");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("89.99"));

        assertThrows(IllegalArgumentException.class,
                () -> settlementEventService.consumeClearingGenerated(request));
    }

    @Test
    void shouldKeepClearingConsumptionIdempotent() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO99999");
        request.setPaymentOrderId("PAY99999");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK99999");
        request.setTargetName("孙阿姨");
        request.setShouldSettleAmount(new BigDecimal("180.00"));
        request.setDeductAmount(new BigDecimal("18.00"));
        request.setNetSettleAmount(new BigDecimal("162.00"));

        SettlementEventDTO first = settlementEventService.consumeClearingGenerated(request);
        SettlementEventDTO second = settlementEventService.consumeClearingGenerated(request);
        PageResultDTO<SettlementOrderDTO> orderResult = settlementOrderService.list("", "", "", "CLO99999", 1, 20);
        PageResultDTO<SettlementEventDTO> eventResult = settlementEventService.list("CLEARING_GENERATED", "CLO99999", 1, 20);

        assertEquals(first.getEventNo(), second.getEventNo());
        assertEquals(1, orderResult.getTotal());
        assertEquals(1, eventResult.getTotal());
    }

    @Test
    void shouldRejectRepeatedClearingConsumptionWhenPaySceneChanges() {
        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO-SCENE-REPLAY");
        request.setPaymentOrderId("PAY-SCENE-REPLAY");
        request.setPayScene("HOME_CLEAN");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK-SCENE-REPLAY");
        request.setTargetName("孙阿姨");
        request.setShouldSettleAmount(new BigDecimal("180.00"));
        request.setDeductAmount(new BigDecimal("18.00"));
        request.setNetSettleAmount(new BigDecimal("162.00"));

        settlementEventService.consumeClearingGenerated(request);
        request.setPayScene("HOME_REPAIR");

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> settlementEventService.consumeClearingGenerated(request));

        assertEquals("重复清分事件与已落库结算事实不一致", exception.getMessage());
    }
}
