package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.accounting.mapper.AccountingDataMapper;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 空库查询边界测试。
 */
class AccountingMemoryStoreEmptyListTest {

    @Test
    void shouldNormalizeNullMapperListsToEmptyLists() {
        AccountingDataMapper mapper = mock(AccountingDataMapper.class);
        when(mapper.findSubjects()).thenReturn(null);
        when(mapper.findAccounts()).thenReturn(null);
        when(mapper.findLedgers()).thenReturn(null);
        when(mapper.findFreezes()).thenReturn(null);
        when(mapper.findAdjustments()).thenReturn(null);
        when(mapper.findEvents()).thenReturn(null);

        AccountingMemoryStore store = new AccountingMemoryStore(mapper);

        assertEmpty(store.subjects());
        assertEmpty(store.accounts());
        assertEmpty(store.ledgers());
        assertEmpty(store.freezes());
        assertEmpty(store.adjustments());
        assertEmpty(store.events());
    }

    @Test
    void shouldFilterNullRowsAndKeepValidSubjects() {
        AccountingDataMapper mapper = mock(AccountingDataMapper.class);
        com.abc123.accounting.entity.AccountSubjectEntity valid =
                mock(com.abc123.accounting.entity.AccountSubjectEntity.class);
        when(mapper.findSubjects()).thenReturn(Arrays.asList(null, valid, null));

        AccountingMemoryStore store = new AccountingMemoryStore(mapper);

        assertTrue(store.subjects().size() == 1);
        assertTrue(store.subjects().get(0) == valid);
    }

    @Test
    void shouldKeepLedgerRowsWhenCreatedAtIsNull() {
        AccountingDataMapper mapper = mock(AccountingDataMapper.class);
        com.abc123.accounting.entity.AccountLedgerEntity first =
                mock(com.abc123.accounting.entity.AccountLedgerEntity.class);
        com.abc123.accounting.entity.AccountLedgerEntity second =
                mock(com.abc123.accounting.entity.AccountLedgerEntity.class);
        when(mapper.findLedgers()).thenReturn(Arrays.asList(first, second));

        AccountingMemoryStore store = new AccountingMemoryStore(mapper);

        assertTrue(store.ledgers().size() == 2);
    }

    private void assertEmpty(List<?> values) {
        assertTrue(values != null && values.isEmpty());
    }
}
