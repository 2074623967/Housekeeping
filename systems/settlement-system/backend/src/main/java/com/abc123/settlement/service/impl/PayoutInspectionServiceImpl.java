package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.PayoutInspectionDTO;
import com.abc123.settlement.entity.PayoutBatchEntity;
import com.abc123.settlement.service.PayoutInspectionService;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 出款批次巡检实现。
 *
 * <p>巡检只读取状态并输出待处理清单，不自动伪造银行回执或改变出款结果。
 */
@Service
public class PayoutInspectionServiceImpl implements PayoutInspectionService {

    private final SettlementMemoryStore settlementMemoryStore;

    public PayoutInspectionServiceImpl(SettlementMemoryStore settlementMemoryStore) {
        this.settlementMemoryStore = settlementMemoryStore;
    }

    @Override
    public PayoutInspectionDTO inspect() {
        List<PayoutBatchEntity> batches = settlementMemoryStore.payoutBatches();
        PayoutInspectionDTO result = new PayoutInspectionDTO();
        result.setTotalBatchCount(batches.size());
        for (PayoutBatchEntity batch : batches) {
            String status = batch.getPayoutStatus();
            if ("待出款".equals(status) || "处理中".equals(status)) {
                result.setPendingBatchCount(result.getPendingBatchCount() + 1);
                result.getPendingBatchNos().add(batch.getPayoutBatchNo());
            } else if ("待重试".equals(status) || "部分失败".equals(status)) {
                result.setFailedBatchCount(result.getFailedBatchCount() + 1);
                result.getFailedBatchNos().add(batch.getPayoutBatchNo());
            } else if ("已完成".equals(status)) {
                result.setCompletedBatchCount(result.getCompletedBatchCount() + 1);
            }
        }
        return result;
    }
}
