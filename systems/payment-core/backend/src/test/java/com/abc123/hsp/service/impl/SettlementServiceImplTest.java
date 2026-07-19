package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 服务者结算单查询条件下推测试。
 */
@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    @Mock
    private SettlementMapper settlementMapper;

    @Test
    void shouldForwardWorkerSettlementQueryToMapper() {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId("SETTLE-001");
        query.setWorkerKeyword("李师傅");
        query.setSettlementStatus("待审核");
        query.setPayoutStatus("待出款");

        new SettlementServiceImpl(settlementMapper).workerList(query);

        verify(settlementMapper).findWorkerSettlements(query);
    }
}
