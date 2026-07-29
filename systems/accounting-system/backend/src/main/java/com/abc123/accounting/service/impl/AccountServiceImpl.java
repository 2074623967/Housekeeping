package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.dto.AccountListItemDTO;
import com.abc123.accounting.dto.OpenAccountRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import com.abc123.accounting.service.AccountService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 账户服务实现。
 */
@Service
public class AccountServiceImpl implements AccountService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public AccountServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<AccountListItemDTO> list(String subjectKeyword, String accountType, String status,
            int pageNo, int pageSize) {
        String keyword = subjectKeyword == null ? "" : subjectKeyword.toLowerCase(Locale.ROOT);
        List<AccountListItemDTO> items = accountingMemoryStore.accounts().stream()
                .filter(item -> keyword.isEmpty()
                        || item.getSubjectId().toLowerCase(Locale.ROOT).contains(keyword)
                        || item.getSubjectName().toLowerCase(Locale.ROOT).contains(keyword))
                .filter(item -> accountType == null || accountType.isEmpty() || accountType.equals(item.getAccountType()))
                .filter(item -> status == null || status.isEmpty() || status.equals(item.getAccountStatus()))
                .map(item -> accountingMapper.toAccountDTO(item, accountingMemoryStore.findBalance(item.getAccountNo())))
                .collect(Collectors.toList());
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }

    @Override
    public AccountListItemDTO open(OpenAccountRequestDTO request) {
        AccountSubjectEntity subjectEntity = accountingMemoryStore.findSubject(request.getSubjectId());
        AccountEntity accountEntity = accountingMemoryStore.createAccount(
                subjectEntity,
                request.getAccountType(),
                request.getCurrency(),
                "正常");
        return accountingMapper.toAccountDTO(accountEntity, accountingMemoryStore.findBalance(accountEntity.getAccountNo()));
    }

    @Override
    public AccountDetailDTO detail(String accountNo) {
        AccountEntity accountEntity = accountingMemoryStore.findAccount(accountNo);
        return accountingMapper.toAccountDetail(
                accountEntity,
                accountingMemoryStore.findBalance(accountNo),
                accountingMemoryStore.ledgers().stream()
                        .filter(item -> accountNo.equals(item.getAccountNo()))
                        .collect(Collectors.toList()));
    }
}
