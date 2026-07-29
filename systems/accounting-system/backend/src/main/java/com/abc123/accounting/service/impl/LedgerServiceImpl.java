package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.service.LedgerService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 流水服务实现。
 */
@Service
public class LedgerServiceImpl implements LedgerService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public LedgerServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<LedgerItemDTO> list(String accountNo, String bizNo, String bizType, int pageNo, int pageSize) {
        String normalizedBizNo = bizNo == null ? "" : bizNo.toLowerCase(Locale.ROOT);
        List<LedgerItemDTO> items = accountingMemoryStore.ledgers().stream()
                .filter(item -> accountNo == null || accountNo.isEmpty() || accountNo.equals(item.getAccountNo()))
                .filter(item -> normalizedBizNo.isEmpty() || item.getBizNo().toLowerCase(Locale.ROOT).contains(normalizedBizNo))
                .filter(item -> bizType == null || bizType.isEmpty() || bizType.equals(item.getBizType()))
                .map(accountingMapper::toLedgerDTO)
                .collect(Collectors.toList());
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
