package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 余额服务测试。
 */
class BalanceServiceImplTest {

    @Test
    void shouldMoveFrozenAmountBackToAvailableWhenUnfreeze() {
        AccountingMemoryStore store = new AccountingMemoryStore();
        store.initDemoData();
        AccountingMapper mapper = new AccountingMapper();
        BalanceServiceImpl balanceService = new BalanceServiceImpl(store, mapper);

        BalanceOperationRequestDTO request = new BalanceOperationRequestDTO();
        request.setAccountNo("ACT10001");
        request.setBizNo("ORD202607200001");
        request.setAmount(new BigDecimal("20.00"));
        request.setOperatorName("运营小王");

        BalanceSnapshotDTO result = balanceService.unfreeze(request);

        assertEquals("¥120.00", result.getAvailableAmount());
        assertEquals("¥48.00", result.getFrozenAmount());
    }
}
