package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.CreateFreezeRequestDTO;
import com.abc123.accounting.dto.FreezeItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.dto.UnfreezeRequestDTO;
import com.abc123.accounting.service.FreezeService;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 冻结服务实现。
 */
@Service
public class FreezeServiceImpl implements FreezeService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public FreezeServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<FreezeItemDTO> list(String accountNo, String freezeStatus, int pageNo, int pageSize) {
        List<FreezeItemDTO> items = accountingMemoryStore.freezes().stream()
                .filter(item -> accountNo == null || accountNo.isEmpty() || accountNo.equals(item.getAccountNo()))
                .filter(item -> freezeStatus == null || freezeStatus.isEmpty() || freezeStatus.equals(item.getFreezeStatus()))
                .map(accountingMapper::toFreezeDTO)
                .collect(Collectors.toList());
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    @Override
    public FreezeItemDTO create(CreateFreezeRequestDTO request) {
        return accountingMapper.toFreezeDTO(accountingMemoryStore.createFreeze(
                request.getAccountNo(),
                request.getBizNo(),
                request.getFreezeType(),
                request.getFreezeReason(),
                request.getFreezeAmount(),
                request.getOperatorName()));
    }

    @Override
    public FreezeItemDTO unfreeze(String freezeNo, UnfreezeRequestDTO request) {
        return accountingMapper.toFreezeDTO(accountingMemoryStore.unfreeze(
                freezeNo,
                request.getOperatorName(),
                request.getUnfreezeReason()));
    }
}
