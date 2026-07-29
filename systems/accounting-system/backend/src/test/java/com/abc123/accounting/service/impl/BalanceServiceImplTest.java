package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;

/**
 * 余额服务测试。
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.BEFORE_EACH_TEST_METHOD)
class BalanceServiceImplTest {

    @Autowired
    private BalanceServiceImpl balanceService;

    @Test
    void shouldMoveFrozenAmountBackToAvailableWhenUnfreeze() {
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
