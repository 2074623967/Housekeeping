package com.abc123.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.wallet.mapper.WalletMapper;
import com.abc123.wallet.entity.WalletAccountEntity;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/**
 * 钱包空库列表边界测试。
 */
class WalletEmptyListBoundaryTest {

    @Test
    void shouldNormalizeNullAccountAndLedgerLists() {
        WalletMapper mapper = mock(WalletMapper.class);
        when(mapper.findAccounts()).thenReturn(null);
        when(mapper.findLedgersByAccountNo("WALLET-EMPTY")).thenReturn(null);
        when(mapper.findAllLedgers("WALLET-EMPTY", null, null)).thenReturn(null);

        WalletServiceImpl service = new WalletServiceImpl(mapper);

        assertTrue(service.listAccounts().isEmpty());
        assertTrue(service.listBalanceSnapshots().isEmpty());
        assertTrue(service.getDetail("WALLET-EMPTY") == null);
        assertTrue(service.listLedgers("WALLET-EMPTY", null, null).isEmpty());
    }

    @Test
    void shouldNormalizeNullRedPacketAndRiskEventLists() {
        WalletMapper mapper = mock(WalletMapper.class);
        when(mapper.findRedPackets()).thenReturn(null);
        when(mapper.findRiskEvents()).thenReturn(null);

        assertTrue(new WalletRedPacketServiceImpl(mapper).listRedPackets().isEmpty());
        assertTrue(new WalletRiskEventServiceImpl(mapper).listRiskEvents().isEmpty());
    }

    @Test
    void shouldFilterNullRowsAndKeepValidAccounts() {
        WalletMapper mapper = mock(WalletMapper.class);
        WalletAccountEntity valid = mock(WalletAccountEntity.class);
        when(mapper.findAccounts()).thenReturn(Arrays.asList(null, valid, null));

        assertTrue(new WalletServiceImpl(mapper).listAccounts().size() == 1);
    }
}
