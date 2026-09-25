package com.abc123.walletaccount.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WalletAccountMapperIntegrationTest {

    @Autowired
    private WalletAccountMapper walletAccountMapper;

    @Test
    void shouldApplyExternalReferenceFilterToCountAndRecords() {
        long total = walletAccountMapper.countAccounts(null, null, null, null, "U10001", null, null, null, null);

        assertEquals(1L, total);
        assertFalse(walletAccountMapper.listAccounts(null, null, null, null, "U10001", null, null, null, null, 0, 20).isEmpty());
    }

    @Test
    void shouldListAccountsWithoutExternalReferenceFilter() {
        assertFalse(walletAccountMapper.listAccounts(null, null, null, null, null, null, null, null, null, 0, 20).isEmpty());
    }
}
