package com.abc123.deposit.dao;

import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
import java.math.BigDecimal;
import java.util.List;

/**
 * 保证金数据访问编排层。
 */
public interface DepositDao {

    int insertAccount(String accountNo, String ownerId, String ownerType, BigDecimal requiredAmount);

    DepositAccountDTO findAccount(String accountNo);

    DepositAccountDTO findAccountByOwner(String ownerId, String ownerType);

    List<DepositAccountDTO> findAccounts(DepositAccountQueryDTO query);

    List<DepositAccountDTO> findAccountsPage(DepositAccountQueryDTO query);

    long countAccounts(DepositAccountQueryDTO query);

    DepositAccountDetailDTO findAccountDetail(String accountNo);

    int updateBalance(String accountNo, BigDecimal expectedBalance, BigDecimal expectedFrozenAmount,
                      BigDecimal balance, BigDecimal frozenAmount);

    int insertFlow(String accountNo, String flowType, BigDecimal amount, BigDecimal beforeBalance,
                   BigDecimal afterBalance, BigDecimal beforeFrozenAmount, BigDecimal afterFrozenAmount,
                   String referenceNo, String remark);

    boolean existsFlowReference(String accountNo, String flowType, String referenceNo);

    List<DepositFlowDTO> findFlows(String accountNo, DepositFlowQueryDTO query);

    List<DepositFlowDTO> findFlowsPage(String accountNo, DepositFlowQueryDTO query);

    long countFlows(String accountNo, DepositFlowQueryDTO query);
}
