package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 出款服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class PayoutServiceImplTest {

    @Autowired
    private PayoutServiceImpl payoutService;

    @Autowired
    private SettlementOrderServiceImpl settlementOrderService;

    @Test
    void shouldCreatePayoutBatchAndRecords() {
        CreatePayoutBatchRequestDTO request = new CreatePayoutBatchRequestDTO();
        request.setBatchNo("SET10001");
        request.setPayoutChannel("BANK");
        request.setCreatedBy("财务专员");

        PayoutBatchDTO result = payoutService.create(request);
        PageResultDTO<PayoutRecordDTO> records = payoutService.records(result.getPayoutBatchNo(), "", 1, 20);

        assertEquals("已完成", result.getPayoutStatus());
        assertEquals(2, records.getTotal());
        assertEquals("已出款", settlementOrderService.detail("SLT20001").getSettlementStatus());
    }

    @Test
    void shouldExecutePendingDraftPayoutBatchAfterAudit() {
        AuditSettlementRequestDTO auditRequest = new AuditSettlementRequestDTO();
        auditRequest.setOperatorName("财务主管");
        auditRequest.setAuditRemark("通过");
        settlementOrderService.audit("SLT20001", auditRequest);

        PageResultDTO<PayoutBatchDTO> payoutBatches = payoutService.list("SET10001", "", 1, 20);
        ExecutePayoutBatchRequestDTO executeRequest = new ExecutePayoutBatchRequestDTO();
        executeRequest.setOperatorName("出款专员");
        executeRequest.setRemark("提交银行出款");

        PayoutBatchDTO executed = payoutService.execute(payoutBatches.getItems().get(0).getPayoutBatchNo(), executeRequest);
        PageResultDTO<PayoutRecordDTO> records = payoutService.records(executed.getPayoutBatchNo(), "", 1, 20);

        assertEquals("已完成", executed.getPayoutStatus());
        assertEquals("已发放", records.getItems().get(0).getPayoutStatus());
        assertEquals("已出款", settlementOrderService.detail("SLT20001").getSettlementStatus());
    }
}
