package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.settlement.common.BusinessException;
import com.abc123.settlement.common.ErrorCode;
import com.abc123.settlement.dto.AuditSettlementRequestDTO;
import com.abc123.settlement.dto.CreatePayoutBatchRequestDTO;
import com.abc123.settlement.dto.ExecutePayoutBatchRequestDTO;
import com.abc123.settlement.dto.PageResultDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.RetryPayoutBatchRequestDTO;
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

        assertEquals("待出款", result.getPayoutStatus());
        assertEquals(0, records.getTotal());
        assertEquals("待审核", settlementOrderService.detail("SLT20001").getSettlementStatus());
    }

    @Test
    void shouldReuseDraftBatchAndAvoidDuplicatePendingRecords() {
        AuditSettlementRequestDTO auditRequest = new AuditSettlementRequestDTO();
        auditRequest.setOperatorName("财务主管");
        auditRequest.setAuditRemark("通过");
        settlementOrderService.audit("SLT20001", auditRequest);

        PayoutBatchDTO first = payoutService.create(createRequest("SET10001"));
        PayoutBatchDTO second = payoutService.create(createRequest("SET10001"));
        PageResultDTO<PayoutRecordDTO> records = payoutService.records(first.getPayoutBatchNo(), "", 1, 20);

        assertEquals(first.getPayoutBatchNo(), second.getPayoutBatchNo());
        assertEquals("待出款", second.getPayoutStatus());
        assertEquals(1, records.getTotal());
        assertEquals("待出款", records.getItems().get(0).getPayoutStatus());
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

    @Test
    void shouldRetryFailedPayoutBatchAndKeepRetryTrace() {
        AuditSettlementRequestDTO auditRequest = new AuditSettlementRequestDTO();
        auditRequest.setOperatorName("财务主管");
        auditRequest.setAuditRemark("通过");
        settlementOrderService.audit("SLT20001", auditRequest);

        PageResultDTO<PayoutBatchDTO> payoutBatches = payoutService.list("SET10001", "", 1, 20);
        ExecutePayoutBatchRequestDTO executeRequest = new ExecutePayoutBatchRequestDTO();
        executeRequest.setOperatorName("出款专员");
        executeRequest.setRemark("提交银行出款");
        executeRequest.setExecutionResult("FAILED");
        executeRequest.setFailureReason("银行通道超时");

        PayoutBatchDTO failedBatch = payoutService.execute(payoutBatches.getItems().get(0).getPayoutBatchNo(), executeRequest);
        PageResultDTO<PayoutRecordDTO> failedRecords = payoutService.records(failedBatch.getPayoutBatchNo(), "", 1, 20);

        assertEquals("待重试", failedBatch.getPayoutStatus());
        assertEquals("已失败", failedRecords.getItems().get(0).getPayoutStatus());
        assertEquals("待出款", settlementOrderService.detail("SLT20001").getSettlementStatus());

        RetryPayoutBatchRequestDTO retryRequest = new RetryPayoutBatchRequestDTO();
        retryRequest.setOperatorName("出款专员");
        retryRequest.setReason("银行恢复后重试");

        PayoutBatchDTO retriedBatch = payoutService.retry(failedBatch.getPayoutBatchNo(), retryRequest);
        PageResultDTO<PayoutRecordDTO> retriedRecords = payoutService.records(retriedBatch.getPayoutBatchNo(), "", 1, 20);

        assertEquals("已完成", retriedBatch.getPayoutStatus());
        assertEquals("已发放", retriedRecords.getItems().get(0).getPayoutStatus());
        assertEquals("1", retriedRecords.getItems().get(0).getRetryCount());
        assertEquals("已出款", settlementOrderService.detail("SLT20001").getSettlementStatus());
    }

    @Test
    void shouldThrowBusinessExceptionWhenPayoutBatchMissing() {
        ExecutePayoutBatchRequestDTO executeRequest = new ExecutePayoutBatchRequestDTO();
        executeRequest.setOperatorName("出款专员");
        executeRequest.setRemark("不存在的批次不应执行");

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> payoutService.execute("PBT-MISSING", executeRequest));

        assertEquals(ErrorCode.PAYOUT_BATCH_NOT_FOUND, exception.getCode());
        assertEquals("出款批次不存在", exception.getMessage());
    }

    private CreatePayoutBatchRequestDTO createRequest(String batchNo) {
        CreatePayoutBatchRequestDTO request = new CreatePayoutBatchRequestDTO();
        request.setBatchNo(batchNo);
        request.setPayoutChannel("BANK");
        request.setCreatedBy("财务专员");
        return request;
    }
}
