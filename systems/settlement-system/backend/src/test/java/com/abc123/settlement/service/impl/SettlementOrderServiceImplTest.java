package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreateSettlementOrderRequestDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import org.junit.jupiter.api.Test;

/**
 * 结算单服务测试。
 */
class SettlementOrderServiceImplTest {

    @Test
    void shouldAuditSettlementOrderToReadyForPayout() {
        SettlementMemoryStore store = new SettlementMemoryStore();
        store.initDemoData();
        SettlementMapper mapper = new SettlementMapper();
        SettlementOrderServiceImpl service = new SettlementOrderServiceImpl(store, mapper);

        CreateSettlementOrderRequestDTO request = new CreateSettlementOrderRequestDTO();
        request.setBatchNo("SET10001");
        request.setTargetType("WORKER");
        request.setTargetNo("WRK1002");
        request.setTargetName("王阿姨");
        request.setShouldSettleAmount(new java.math.BigDecimal("50.00"));
        request.setDeductAmount(new java.math.BigDecimal("5.00"));
        SettlementOrderDTO created = service.create(request);

        AuditSettlementRequestDTO audit = new AuditSettlementRequestDTO();
        audit.setOperatorName("财务主管");
        audit.setAuditRemark("通过");

        SettlementOrderDTO audited = service.audit(created.getSettlementNo(), audit);

        assertEquals("已通过", audited.getAuditStatus());
        assertEquals("待出款", audited.getSettlementStatus());
    }
}
