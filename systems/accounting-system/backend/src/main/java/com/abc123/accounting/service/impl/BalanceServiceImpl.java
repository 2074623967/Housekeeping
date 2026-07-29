package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.BalanceOperationRequestDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.service.BalanceService;
import org.springframework.stereotype.Service;

/**
 * 余额服务实现。
 */
@Service
public class BalanceServiceImpl implements BalanceService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public BalanceServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public BalanceSnapshotDTO detail(String accountNo) {
        AccountEntity accountEntity = accountingMemoryStore.findAccount(accountNo);
        return accountingMapper.toBalanceDTO(accountEntity, accountingMemoryStore.findBalance(accountNo));
    }

    @Override
    public BalanceSnapshotDTO credit(BalanceOperationRequestDTO request) {
        accountingMemoryStore.credit(
                request.getAccountNo(),
                request.getBizType(),
                request.getBizNo(),
                request.getAmount(),
                request.getOperatorName());
        return detail(request.getAccountNo());
    }

    @Override
    public BalanceSnapshotDTO freeze(BalanceOperationRequestDTO request) {
        accountingMemoryStore.freeze(
                request.getAccountNo(),
                request.getBizNo(),
                request.getRemark(),
                request.getAmount(),
                request.getOperatorName());
        return detail(request.getAccountNo());
    }

    @Override
    public BalanceSnapshotDTO unfreeze(BalanceOperationRequestDTO request) {
        accountingMemoryStore.unfreezeBalanceOnly(
                request.getAccountNo(),
                request.getBizNo(),
                request.getAmount(),
                request.getOperatorName());
        return detail(request.getAccountNo());
    }
}
