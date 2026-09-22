package com.abc123.clearing;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.clearing.mapper.ClearingDataMapper;
import com.abc123.clearing.service.impl.ClearingMemoryStore;
import org.junit.jupiter.api.Test;

/**
 * 清分持久化数据仓空结果边界测试。
 */
class ClearingMemoryStoreEmptyListTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyLists() {
        ClearingDataMapper mapper = mock(ClearingDataMapper.class);
        when(mapper.findBatches()).thenReturn(null);
        when(mapper.findOrders()).thenReturn(null);
        when(mapper.findRules()).thenReturn(null);
        when(mapper.findFeeRules()).thenReturn(null);
        when(mapper.findShares()).thenReturn(null);
        when(mapper.findEvents()).thenReturn(null);
        when(mapper.findDueFailedClearingGeneratedEvents("2026-01-01 00:00:00", 1)).thenReturn(null);

        ClearingMemoryStore store = new ClearingMemoryStore(mapper, "ACT10002");

        assertTrue(store.batches().isEmpty());
        assertTrue(store.orders().isEmpty());
        assertTrue(store.rules().isEmpty());
        assertTrue(store.feeRules().isEmpty());
        assertTrue(store.shares().isEmpty());
        assertTrue(store.sharesByClearingNo("CLR-EMPTY").isEmpty());
        assertTrue(store.events().isEmpty());
        assertTrue(store.dueFailedClearingGeneratedEvents(1).isEmpty());
    }
}
