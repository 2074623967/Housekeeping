package com.abc123.deposit.dao.impl;

import com.abc123.deposit.dao.DepositDao;
import com.abc123.deposit.dto.DepositAccountDTO;
import com.abc123.deposit.dto.DepositAccountDetailDTO;
import com.abc123.deposit.dto.DepositAccountQueryDTO;
import com.abc123.deposit.dto.DepositFlowDTO;
import com.abc123.deposit.dto.DepositFlowQueryDTO;
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
    public DepositAccountDTO findAccountByOwner(String ownerId, String ownerType) {
        return mapper.findAccountByOwner(ownerId, ownerType);
    }

    @Override
    public List<DepositAccountDTO> findAccounts(DepositAccountQueryDTO query) {
        return mapper.findAccounts(query == null ? null : query.getOwnerType(),
                query == null ? null : query.getStatus(),
                query == null ? null : query.getKeyword());
    }

    @Override
    public List<DepositAccountDTO> findAccountsPage(DepositAccountQueryDTO query) {
        return mapper.findAccountsPage(query == null ? null : query.getOwnerType(),
                query == null ? null : query.getStatus(), query == null ? null : query.getKeyword(),
                query == null ? 1 : query.getPageNo(), query == null ? 20 : query.getPageSize(),
                query == null ? 0 : query.getOffset());
    }

    @Override
    public long countAccounts(DepositAccountQueryDTO query) {
        return mapper.countAccounts(query == null ? null : query.getOwnerType(),
                query == null ? null : query.getStatus(), query == null ? null : query.getKeyword());
    }

    @Override
    public DepositAccountDetailDTO findAccountDetail(String accountNo) {
        return mapper.findAccountDetail(accountNo);
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
    public boolean existsFlowReference(String accountNo, String flowType, String referenceNo) {
        return mapper.countFlowReference(accountNo, flowType, referenceNo) > 0;
    }

    @Override
    public List<DepositFlowDTO> findFlows(String accountNo, DepositFlowQueryDTO query) {
        return mapper.findFlows(accountNo,
                query == null ? null : query.getFlowType(),
                query == null ? null : query.getReferenceNo());
    }

    @Override
    public List<DepositFlowDTO> findFlowsPage(String accountNo, DepositFlowQueryDTO query) {
        return mapper.findFlowsPage(accountNo, query == null ? null : query.getFlowType(),
                query == null ? null : query.getReferenceNo(), query == null ? 1 : query.getPageNo(),
                query == null ? 20 : query.getPageSize(), query == null ? 0 : query.getOffset());
    }

    @Override
    public long countFlows(String accountNo, DepositFlowQueryDTO query) {
        return mapper.countFlows(accountNo, query == null ? null : query.getFlowType(),
                query == null ? null : query.getReferenceNo());
    }
}
