package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AccountSubjectDTO;
import com.abc123.accounting.dto.CreateAccountSubjectRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.entity.AccountSubjectEntity;
import com.abc123.accounting.service.AccountSubjectService;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 账户主体服务实现。
 */
@Service
public class AccountSubjectServiceImpl implements AccountSubjectService {

    private final AccountingMemoryStore accountingMemoryStore;
    private final AccountingMapper accountingMapper;

    public AccountSubjectServiceImpl(AccountingMemoryStore accountingMemoryStore, AccountingMapper accountingMapper) {
        this.accountingMemoryStore = accountingMemoryStore;
        this.accountingMapper = accountingMapper;
    }

    @Override
    public PageResultDTO<AccountSubjectDTO> list(String keyword, String subjectType, String status, int pageNo, int pageSize) {
        String normalizedKeyword = keyword == null ? "" : keyword.toLowerCase(Locale.ROOT);
        List<AccountSubjectDTO> filtered = accountingMemoryStore.subjects().stream()
                .filter(item -> normalizedKeyword.isEmpty()
                        || item.getSubjectName().toLowerCase(Locale.ROOT).contains(normalizedKeyword)
                        || item.getOwnerName().toLowerCase(Locale.ROOT).contains(normalizedKeyword))
                .filter(item -> subjectType == null || subjectType.isEmpty() || subjectType.equals(item.getSubjectType()))
                .filter(item -> status == null || status.isEmpty() || status.equals(item.getStatus()))
                .map(item -> accountingMapper.toSubjectDTO(
                        item,
                        (int) accountingMemoryStore.accounts().stream()
                                .filter(account -> account.getSubjectId().equals(item.getSubjectId()))
                                .count()))
                .collect(Collectors.toList());
        return paginate(filtered, pageNo, pageSize);
    }

    @Override
    public AccountSubjectDTO create(CreateAccountSubjectRequestDTO request) {
        AccountSubjectEntity entity = accountingMemoryStore.createSubject(
                request.getSubjectType(),
                request.getSubjectName(),
                request.getOwnerName(),
                "启用");
        return accountingMapper.toSubjectDTO(entity, 0);
    }

    @Override
    public AccountSubjectDTO detail(String subjectId) {
        AccountSubjectEntity entity = accountingMemoryStore.findSubject(subjectId);
        return accountingMapper.toSubjectDTO(
                entity,
                (int) accountingMemoryStore.accounts().stream()
                        .filter(account -> account.getSubjectId().equals(subjectId))
                        .count());
    }

    private PageResultDTO<AccountSubjectDTO> paginate(List<AccountSubjectDTO> items, int pageNo, int pageSize) {
        int safePageNo = Math.max(pageNo, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePageNo - 1) * safePageSize, items.size());
        int toIndex = Math.min(fromIndex + safePageSize, items.size());
        return new PageResultDTO<>(items.subList(fromIndex, toIndex), items.size(), safePageNo, safePageSize);
    }
}
