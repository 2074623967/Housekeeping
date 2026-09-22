package com.abc123.settlement.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.abc123.settlement.entity.PayoutBatchEntity;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * 出款巡检服务测试。
 */
class PayoutInspectionServiceImplTest {

    @Test
    void shouldClassifyPendingFailedAndCompletedBatches() {
        SettlementMemoryStore store = Mockito.mock(SettlementMemoryStore.class);
        when(store.payoutBatches()).thenReturn(Arrays.asList(
                batch("PBT-1", "待出款"),
                batch("PBT-2", "部分失败"),
                batch("PBT-3", "已完成")));

        PayoutInspectionServiceImpl service = new PayoutInspectionServiceImpl(store);

        assertEquals(3, service.inspect().getTotalBatchCount());
        assertEquals(1, service.inspect().getPendingBatchCount());
        assertEquals(1, service.inspect().getFailedBatchCount());
        assertEquals(1, service.inspect().getCompletedBatchCount());
        assertEquals("PBT-2", service.inspect().getFailedBatchNos().get(0));
    }

    private PayoutBatchEntity batch(String batchNo, String status) {
        PayoutBatchEntity batch = new PayoutBatchEntity();
        batch.setPayoutBatchNo(batchNo);
        batch.setPayoutStatus(status);
        return batch;
    }
}
