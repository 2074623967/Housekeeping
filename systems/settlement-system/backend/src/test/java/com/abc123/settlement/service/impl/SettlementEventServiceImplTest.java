package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
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
        request.setTargetType("WORKER");
        request.setTargetNo("WRK88888");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));

        SettlementEventDTO result = settlementEventService.consumeClearingGenerated(request);
        PageResultDTO<SettlementOrderDTO> orderResult = settlementOrderService.list("", "", "", "CLO88888", 1, 20);

        assertEquals("CLEARING_GENERATED", result.getEventType());
        assertEquals(2, settlementBatchService.list("", "", 1, 20).getTotal());
        assertEquals(1, orderResult.getTotal());
        assertEquals("CLO88888", orderResult.getItems().get(0).getClearingNo());
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
}
