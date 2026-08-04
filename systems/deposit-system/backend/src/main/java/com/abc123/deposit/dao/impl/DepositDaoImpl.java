package com.abc123.deposit.dao.impl;

import com.abc123.deposit.dao.DepositDao;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.mapper.DepositMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/**
 * MyBatis 保证金数据访问实现。
 */
@Repository
public class DepositDaoImpl implements DepositDao {

    private final DepositMapper mapper;

    public DepositDaoImpl(DepositMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public int insertAccount(String accountNo, String ownerId, String ownerType, BigDecimal requiredAmount) {
        return mapper.insertAccount(accountNo, ownerId, ownerType, requiredAmount);
    }

    @Override
    public DepositAccountDTO findAccount(String accountNo) {
        return mapper.findAccount(accountNo);
    }

    @Override
    public List<DepositAccountDTO> findAccounts() {
        return mapper.findAccounts();
    }

    @Override
    public int updateBalance(String accountNo, BigDecimal expectedBalance, BigDecimal expectedFrozenAmount,
                             BigDecimal balance, BigDecimal frozenAmount) {
        return mapper.updateBalance(accountNo, expectedBalance, expectedFrozenAmount, balance, frozenAmount);
    }

    @Override
    public int insertFlow(String accountNo, String flowType, BigDecimal amount, BigDecimal beforeBalance,
                          BigDecimal afterBalance, BigDecimal beforeFrozenAmount, BigDecimal afterFrozenAmount,
                          String referenceNo, String remark) {
        return mapper.insertFlow(accountNo, flowType, amount, beforeBalance, afterBalance,
                beforeFrozenAmount, afterFrozenAmount, referenceNo, remark);
    }

    @Override
    public List<DepositFlowDTO> findFlows(String accountNo) {
        return mapper.findFlows(accountNo);
    }
}
