package com.abc123.accounting.service.impl;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountBalanceEntity;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/**
 * 账户详情空流水边界测试。
 */
class AccountingMapperEmptyListTest {

    @Test
    void shouldReturnEmptyRecentLedgersWhenInputIsNull() {
        AccountEntity account = new AccountEntity();
        account.setAccountNo("ACT-EMPTY");
        account.setSubjectName("空库主体");
        account.setAccountType("用户可用账户");
        account.setAccountStatus("正常");
        AccountBalanceEntity balance = new AccountBalanceEntity();
        balance.setAvailableAmount(BigDecimal.ZERO);
        balance.setFrozenAmount(BigDecimal.ZERO);
        balance.setInTransitAmount(BigDecimal.ZERO);

        AccountDetailDTO detail = new AccountingMapper().toAccountDetail(
                account,
                balance,
                null);

        assertNotNull(detail.getRecentLedgers());
        assertTrue(detail.getRecentLedgers().isEmpty());
    }
}
