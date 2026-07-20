package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 结算事件服务测试。
 */
class SettlementEventServiceImplTest {

    @Test
    void shouldGenerateSettlementOrderWhenClearingEventConsumed() {
        SettlementMemoryStore store = new SettlementMemoryStore();
        store.initDemoData();
        SettlementMapper mapper = new SettlementMapper();
        SettlementEventServiceImpl service = new SettlementEventServiceImpl(store, mapper);

        ClearingGeneratedEventRequestDTO request = new ClearingGeneratedEventRequestDTO();
        request.setClearingNo("CLO88888");
        request.setPaymentOrderId("PAY88888");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK88888");
        request.setTargetName("赵阿姨");
        request.setShouldSettleAmount(new BigDecimal("100.00"));
        request.setDeductAmount(new BigDecimal("10.00"));
        request.setNetSettleAmount(new BigDecimal("90.00"));

        SettlementEventDTO result = service.consumeClearingGenerated(request);

        assertEquals("CLEARING_GENERATED", result.getEventType());
    }
}
