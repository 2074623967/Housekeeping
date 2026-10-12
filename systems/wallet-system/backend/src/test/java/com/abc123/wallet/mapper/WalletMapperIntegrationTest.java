package com.abc123.wallet.mapper;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.wallet.dto.WalletRechargeOrderDTO;
import com.abc123.wallet.dto.WalletRechargeRequestDTO;
import com.abc123.wallet.service.WalletRechargeService;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
class WalletMapperIntegrationTest {

    @Autowired
    private WalletMapper walletMapper;

    @Autowired
    private WalletRechargeService walletRechargeService;

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

    @Test
    @Transactional
    void shouldCreatePendingRechargeWithoutCreditingAndReuseMatchingBusinessNumber() {
        WalletRechargeRequestDTO request = new WalletRechargeRequestDTO();
        request.setAccountNo("WALLET-10001");
        request.setBizNo("RCH-PENDING-TEST");
        request.setAmount(new BigDecimal("30.00"));
        String balance = walletMapper.findAccountByNo(request.getAccountNo()).getAvailableAmount();
        int ledgerCount = walletMapper.findLedgersByAccountNo(request.getAccountNo()).size();

        WalletRechargeOrderDTO created = walletRechargeService.recharge(request);
        WalletRechargeOrderDTO retried = walletRechargeService.recharge(request);

        assertEquals("PENDING", created.getStatus());
        assertEquals(created.getRechargeNo(), retried.getRechargeNo());
        assertEquals(balance, walletMapper.findAccountByNo("WALLET-10001").getAvailableAmount());
        assertEquals(ledgerCount, walletMapper.findLedgersByAccountNo(request.getAccountNo()).size());

        request.setAmount(new BigDecimal("31.00"));
        assertThrows(IllegalArgumentException.class, () -> walletRechargeService.recharge(request));
        request.setAmount(new BigDecimal("30.00"));
        request.setAccountNo("WALLET-10002");
        assertThrows(IllegalArgumentException.class, () -> walletRechargeService.recharge(request));
        assertThrows(DataIntegrityViolationException.class, () -> walletMapper.insertRechargeOrder(
                "RCH-DUPLICATE-TEST", "WALLET-10001", request.getBizNo(), new BigDecimal("30.00"), "PENDING"));
        assertEquals(balance, walletMapper.findAccountByNo("WALLET-10001").getAvailableAmount());
    }

    @Test
    @Transactional
    void shouldRejectRechargeWithMissingBusinessNumber() {
        WalletRechargeRequestDTO request = new WalletRechargeRequestDTO();
        request.setAccountNo("WALLET-10001");
        request.setAmount(new BigDecimal("30.00"));

        assertThrows(IllegalArgumentException.class, () -> walletRechargeService.recharge(request));
    }
}
