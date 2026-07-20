package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.dto.SettlementOrderDetailDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 结算单服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class SettlementOrderServiceImplTest {

    @Autowired
    private SettlementOrderServiceImpl settlementOrderService;

    @Test
    void shouldAuditSettlementOrderToReadyForPayout() {
        AuditSettlementRequestDTO audit = new AuditSettlementRequestDTO();
        audit.setOperatorName("财务主管");
        audit.setAuditRemark("通过");

        SettlementOrderDTO audited = settlementOrderService.audit("SLT20001", audit);
        SettlementOrderDetailDTO detail = settlementOrderService.fullDetail("SLT20001");

        assertEquals("已通过", audited.getAuditStatus());
        assertEquals("待出款", audited.getSettlementStatus());
        assertEquals(2, detail.getItems().size());
    }
}
