package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
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

        assertEquals("CLEARING_GENERATED", result.getEventType());
        assertEquals(2, settlementBatchService.list("", "", 1, 20).getTotal());
    }
}
