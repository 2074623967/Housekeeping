package com.abc123.wallet.service.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.abc123.wallet.entity.WalletLedgerEntity;
import com.abc123.wallet.mapper.WalletMapper;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;

/**
 * 钱包查询服务导出测试。
 */
class WalletServiceImplTest {

    @Test
    void shouldExportWalletLedgers() {
        WalletMapper walletMapper = mock(WalletMapper.class);
        WalletLedgerEntity entity = new WalletLedgerEntity();
        entity.setLedgerNo("WLG-001");
        entity.setAccountNo("WALLET-001");
        entity.setBizType("BALANCE_PAY");
        entity.setBizNo("ORDER-001");
        entity.setAmount("88.00");
        entity.setDirection("OUT");
        entity.setCreatedAt("2026-08-07 09:40:00");
        when(walletMapper.findAllLedgers("WALLET-001", "BALANCE_PAY", "OUT"))
                .thenReturn(Collections.singletonList(entity));

        WalletServiceImpl service = new WalletServiceImpl(walletMapper);
        String csv = new String(service.exportLedgers("WALLET-001", "BALANCE_PAY", "OUT"), StandardCharsets.UTF_8);

        assertTrue(csv.contains("流水号,钱包账户号,业务类型"));
        assertTrue(csv.contains("WLG-001"));
        assertTrue(csv.contains("ORDER-001"));
    }
}
