package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.WorkerSettlementListItemDTO;
import com.abc123.hsp.dto.WorkerSettlementOverviewDTO;
import com.abc123.hsp.dto.WorkerSettlementQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
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
    void shouldRejectOversizedWorkerSettlementQueryBeforeMapperAccess() {
        WorkerSettlementQueryDTO settlementOrderQuery = new WorkerSettlementQueryDTO();
        settlementOrderQuery.setSettlementOrderId(repeat("S", 65));
        WorkerSettlementQueryDTO settlementOrderAfterTrimQuery = new WorkerSettlementQueryDTO();
        settlementOrderAfterTrimQuery.setSettlementOrderId(repeat("S", 64) + " ");
        WorkerSettlementQueryDTO workerQuery = new WorkerSettlementQueryDTO();
        workerQuery.setWorkerKeyword(repeat("李", 129));
        WorkerSettlementQueryDTO settlementStatusQuery = new WorkerSettlementQueryDTO();
        settlementStatusQuery.setSettlementStatus(repeat("A", 33));
        WorkerSettlementQueryDTO payoutStatusQuery = new WorkerSettlementQueryDTO();
        payoutStatusQuery.setPayoutStatus(repeat("P", 33));
        SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper);

        assertThrows(IllegalArgumentException.class, () -> service.workerList(settlementOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.workerList(settlementOrderAfterTrimQuery));
        assertThrows(IllegalArgumentException.class, () -> service.workerOverview(workerQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(settlementStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.workerList(payoutStatusQuery));
        verifyNoInteractions(settlementMapper);
    }

    @Test
    void shouldRejectControlCharactersInWorkerSettlementQueryBeforeMapperAccess() {
        WorkerSettlementQueryDTO settlementOrderQuery = new WorkerSettlementQueryDTO();
        settlementOrderQuery.setSettlementOrderId("SETTLE-001\n");
        WorkerSettlementQueryDTO workerQuery = new WorkerSettlementQueryDTO();
        workerQuery.setWorkerKeyword("李师傅\n");
        WorkerSettlementQueryDTO settlementStatusQuery = new WorkerSettlementQueryDTO();
        settlementStatusQuery.setSettlementStatus("待审核\n");
        WorkerSettlementQueryDTO payoutStatusQuery = new WorkerSettlementQueryDTO();
        payoutStatusQuery.setPayoutStatus("待出款\n");
        SettlementServiceImpl service = new SettlementServiceImpl(settlementMapper);

        assertThrows(IllegalArgumentException.class, () -> service.workerList(settlementOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.workerOverview(workerQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(settlementStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.workerList(payoutStatusQuery));
        verifyNoInteractions(settlementMapper);
    }

    @Test
    void shouldMaskWorkerSettlementListWithoutChangingMapperDto() {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        WorkerSettlementListItemDTO item = new WorkerSettlementListItemDTO();
        item.setWorkerName("李师傅");
        when(settlementMapper.findWorkerSettlements(query))
                .thenReturn(java.util.Arrays.asList((WorkerSettlementListItemDTO) null, item));
        when(settlementMapper.count(query)).thenReturn(1L);

        PageResultDTO<WorkerSettlementListItemDTO> result =
                new SettlementServiceImpl(settlementMapper).workerList(query);

        assertEquals("李*", result.getItems().get(0).getWorkerName());
        assertEquals("李师傅", item.getWorkerName());
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
        item.setStatus("待审核");
        item.setStatusType("warn");
        item.setPayoutStatus("待出款");
        item.setPayoutStatusType("warn");
        when(settlementMapper.findWorkerSettlementsForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new SettlementServiceImpl(settlementMapper).exportCsv(query);

        assertEquals("SETTLE-001", query.getSettlementOrderId());
        assertEquals("李师傅", query.getWorkerKeyword());
        assertTrue(csv.contains("SETTLE-001"));
        assertTrue(csv.contains("结算状态,结算状态样式,出款状态,出款状态样式"));
        assertTrue(csv.contains("\"待审核\",\"warn\",\"待出款\",\"warn\""));
        assertTrue(csv.contains("李*"));
        assertFalse(csv.contains("李师傅"));
        assertTrue(csv.contains("¥888.00"));
        assertEquals("李师傅", item.getWorkerName());
        verify(settlementMapper).findWorkerSettlementsForExport(query);
    }

    @Test
    void shouldReturnWorkerSettlementOverview() {
        WorkerSettlementQueryDTO query = new WorkerSettlementQueryDTO();
        query.setSettlementOrderId(" SETTLE-001 ");
        query.setWorkerKeyword(" 李师傅 ");
        query.setSettlementStatus(" 待审核 ");
        query.setPayoutStatus(" 待出款 ");
        WorkerSettlementOverviewDTO overview = new WorkerSettlementOverviewDTO();
        overview.setTotalSettlementCount(6L);
        overview.setNegativeNetSettleCount(1);
        when(settlementMapper.findWorkerOverview(query)).thenReturn(overview);

        WorkerSettlementOverviewDTO result = new SettlementServiceImpl(settlementMapper).workerOverview(query);

        assertEquals("SETTLE-001", query.getSettlementOrderId());
        assertEquals("李师傅", query.getWorkerKeyword());
        assertEquals("待审核", query.getSettlementStatus());
        assertEquals("待出款", query.getPayoutStatus());
        assertEquals(6L, result.getTotalSettlementCount());
        assertEquals(1, result.getNegativeNetSettleCount());
        assertEquals("¥0.00", result.getTotalNetSettleAmount());
        verify(settlementMapper).findWorkerOverview(query);
    }

    @Test
    void shouldUseDefaultQueryWhenListingWorkerSettlementsWithNullQuery() {
        when(settlementMapper.findWorkerSettlements(org.mockito.ArgumentMatchers.any(WorkerSettlementQueryDTO.class)))
                .thenReturn(Collections.emptyList());
        when(settlementMapper.count(org.mockito.ArgumentMatchers.any(WorkerSettlementQueryDTO.class))).thenReturn(0L);

        PageResultDTO<WorkerSettlementListItemDTO> result =
                new SettlementServiceImpl(settlementMapper).workerList(null);

        assertEquals(0, result.getTotal());
        org.mockito.ArgumentCaptor<WorkerSettlementQueryDTO> captor =
                org.mockito.ArgumentCaptor.forClass(WorkerSettlementQueryDTO.class);
        verify(settlementMapper).findWorkerSettlements(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
