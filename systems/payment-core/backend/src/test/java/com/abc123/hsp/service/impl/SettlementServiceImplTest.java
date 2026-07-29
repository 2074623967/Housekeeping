package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.mapper.SettlementMapper;
import java.util.Collections;
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
        query.setPageNo(2);
        query.setPageSize(0);

        when(settlementMapper.findWorkerSettlements(query)).thenReturn(Collections.emptyList());
        when(settlementMapper.count(query)).thenReturn(0L);
        new SettlementServiceImpl(settlementMapper).workerList(query);

        assertEquals(2, query.getPageNo());
        assertEquals(1, query.getPageSize());
        verify(settlementMapper).findWorkerSettlements(query);
        verify(settlementMapper).count(query);
    }

    @Test
    void shouldExportWorkerSettlements() {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(" SETTLE-001 ");
        query.setWorkerKeyword(" 李师傅 ");
        WorkerSettlementListItemDTO item = new WorkerSettlementListItemDTO();
        item.setSettlementOrderId("SETTLE-001");
        item.setWorkerName("李师傅");
        item.setAmountNetSettle("¥888.00");
        when(settlementMapper.findWorkerSettlementsForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new SettlementServiceImpl(settlementMapper).exportCsv(query);

        assertEquals("SETTLE-001", query.getSettlementOrderId());
        assertEquals("李师傅", query.getWorkerKeyword());
        assertTrue(csv.contains("SETTLE-001"));
        assertTrue(csv.contains("李师傅"));
        assertTrue(csv.contains("¥888.00"));
        verify(settlementMapper).findWorkerSettlementsForExport(query);
    }
}
