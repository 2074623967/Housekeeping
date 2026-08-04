package com.abc123.deposit.dao;

import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import java.math.BigDecimal;
import java.util.List;

/**
 * 保证金数据访问编排层。
 */
public interface DepositDao {

    int insertAccount(String accountNo, String ownerId, String ownerType, BigDecimal requiredAmount);

    DepositAccountDTO findAccount(String accountNo);

    List<DepositAccountDTO> findAccounts();

    int updateBalance(String accountNo, BigDecimal expectedBalance, BigDecimal expectedFrozenAmount,
                      BigDecimal balance, BigDecimal frozenAmount);

    int insertFlow(String accountNo, String flowType, BigDecimal amount, BigDecimal beforeBalance,
                   BigDecimal afterBalance, BigDecimal beforeFrozenAmount, BigDecimal afterFrozenAmount,
                   String referenceNo, String remark);

    List<DepositFlowDTO> findFlows(String accountNo);
}
