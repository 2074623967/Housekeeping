package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.settlement.common.BusinessException;
import com.abc123.settlement.common.ErrorCode;
import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
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

    @Autowired
    private PayoutServiceImpl payoutService;

    @Test
    void shouldAuditSettlementOrderToReadyForPayout() {
        AuditSettlementRequestDTO audit = new AuditSettlementRequestDTO();
        audit.setOperatorName("财务主管");
        audit.setAuditRemark("通过");

        SettlementOrderDTO audited = settlementOrderService.audit("SLT20001", audit);
        SettlementOrderDetailDTO detail = settlementOrderService.fullDetail("SLT20001");
        PageResultDTO<PayoutBatchDTO> payoutBatches = payoutService.list("SET10001", "", 1, 20);
        PageResultDTO<PayoutRecordDTO> payoutRecords = payoutService.records(
                payoutBatches.getItems().get(0).getPayoutBatchNo(),
                "",
                1,
                20);

        assertEquals("已通过", audited.getAuditStatus());
        assertEquals("success", audited.getAuditStatusType());
        assertEquals("待出款", audited.getSettlementStatus());
        assertEquals(2, detail.getItems().size());
        assertEquals(1, payoutBatches.getTotal());
        assertEquals("待出款", payoutBatches.getItems().get(0).getPayoutStatus());
        assertEquals(1, payoutRecords.getTotal());
        assertEquals("待出款", payoutRecords.getItems().get(0).getPayoutStatus());
    }

    @Test
    void shouldThrowBusinessExceptionWhenSettlementOrderMissing() {
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> settlementOrderService.fullDetail("SLT-MISSING"));

        assertEquals(ErrorCode.SETTLEMENT_ORDER_NOT_FOUND, exception.getCode());
        assertEquals("结算单不存在", exception.getMessage());
    }
}
