package com.abc123.wallet.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WalletMapperIntegrationTest {

    @Autowired
    private WalletMapper walletMapper;

    @Test
    void shouldReadSeededWalletDataWithEmbeddedDatabase() {
        assertFalse(walletMapper.findAccounts().isEmpty());
        assertFalse(walletMapper.findAllLedgers(null, null, null).isEmpty());
        assertFalse(walletMapper.findRedPackets().isEmpty());
        assertFalse(walletMapper.findRiskEvents().isEmpty());
        assertNotNull(walletMapper.findAccounts().get(0).getAvailableAmount());
        assertNotNull(walletMapper.findAccounts().get(0).getCreatedAt());
    }

    @Test
    void shouldRejectAtomicDebitWhenAvailableBalanceIsInsufficient() {
        int updated = walletMapper.updateAccountAmount("WALLET-10001", new BigDecimal("-999999.00"));

        assertEquals(0, updated);
        assertEquals("268.00", walletMapper.findAccountByNo("WALLET-10001").getAvailableAmount());
    }
}
