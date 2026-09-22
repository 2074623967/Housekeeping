package com.abc123.reconciliation;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.reconciliation.dao.impl.ReconciliationDaoImpl;
import com.abc123.reconciliation.dto.BatchQueryDTO;
import com.abc123.reconciliation.mapper.ReconciliationMapper;
import org.junit.jupiter.api.Test;

/**
 * 对账 DAO 空结果边界测试。
 */
class ReconciliationDaoEmptyListTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyLists() {
        ReconciliationMapper mapper = mock(ReconciliationMapper.class);
        when(mapper.findBatches(org.mockito.ArgumentMatchers.any(BatchQueryDTO.class))).thenReturn(null);
        when(mapper.findChannelRecords("BATCH-EMPTY")).thenReturn(null);
        when(mapper.findInternalRecords("BATCH-EMPTY")).thenReturn(null);
        when(mapper.findChannelRecordDetails("BATCH-EMPTY")).thenReturn(null);
        when(mapper.findInternalRecordDetails("BATCH-EMPTY")).thenReturn(null);
        when(mapper.findDifferences(null)).thenReturn(null);
        when(mapper.findDifferencesByBatch("BATCH-EMPTY")).thenReturn(null);
        when(mapper.findCrossSystemFacts("CHAIN-EMPTY")).thenReturn(null);

        ReconciliationDaoImpl dao = new ReconciliationDaoImpl(mapper);

        assertTrue(dao.findBatches(new BatchQueryDTO()).isEmpty());
        assertTrue(dao.findChannelRecords("BATCH-EMPTY").isEmpty());
        assertTrue(dao.findInternalRecords("BATCH-EMPTY").isEmpty());
        assertTrue(dao.findChannelRecordDetails("BATCH-EMPTY").isEmpty());
        assertTrue(dao.findInternalRecordDetails("BATCH-EMPTY").isEmpty());
        assertTrue(dao.findDifferences(null).isEmpty());
        assertTrue(dao.findDifferencesByBatch("BATCH-EMPTY").isEmpty());
        assertTrue(dao.findCrossSystemFacts("CHAIN-EMPTY").isEmpty());
    }
}
