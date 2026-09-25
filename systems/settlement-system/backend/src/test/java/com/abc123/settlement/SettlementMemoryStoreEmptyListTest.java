package com.abc123.settlement;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.settlement.entity.SettlementBatchEntity;
import com.abc123.settlement.mapper.SettlementDataMapper;
import com.abc123.settlement.service.impl.SettlementMemoryStore;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 结算持久化数据仓空结果边界测试。
 */
class SettlementMemoryStoreEmptyListTest {

    @Test
    void shouldFilterNullRowsAndKeepValidBatches() {
        SettlementDataMapper mapper = mock(SettlementDataMapper.class);
        SettlementBatchEntity valid = mock(SettlementBatchEntity.class);
        when(mapper.findBatches()).thenReturn(Arrays.asList(null, valid, null));

        SettlementMemoryStore store = new SettlementMemoryStore(mapper);

        assertTrue(store.batches().size() == 1);
        assertSame(valid, store.batches().get(0));
    }
}
