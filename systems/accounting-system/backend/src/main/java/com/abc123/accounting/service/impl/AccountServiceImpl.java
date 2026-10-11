package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.dto.AccountListItemDTO;
import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.dto.OpenAccountRequestDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import com.abc123.accounting.service.AccountService;
import java.nio.charset.StandardCharsets;
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
        List<AccountLedgerEntity> ledgers = findLedgersByAccountNo(accountNo);
        return accountingMapper.toAccountDetail(
                accountEntity,
                accountingMemoryStore.findBalance(accountNo),
                ledgers);
    }

    /**
     * 导出当前账户详情页快照，包含账户主信息、余额信息和流水留档。
     */
    @Override
    public byte[] exportDetailSnapshot(String accountNo) {
        AccountEntity accountEntity = accountingMemoryStore.findAccount(accountNo);
        List<AccountLedgerEntity> ledgers = findLedgersByAccountNo(accountNo);
        AccountDetailDTO detail = accountingMapper.toAccountDetail(
                accountEntity,
                accountingMemoryStore.findBalance(accountNo),
                ledgers);
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("账户号,主体ID,主体名称,账户类型,账户状态,状态样式,币种,可用余额,冻结余额,在途余额,最近变更时间\n");
        builder.append(csv(detail.getAccount().getAccountNo())).append(',')
                .append(csv(detail.getAccount().getSubjectId())).append(',')
                .append(csv(detail.getAccount().getSubjectName())).append(',')
                .append(csv(detail.getAccount().getAccountType())).append(',')
                .append(csv(detail.getAccount().getAccountStatus())).append(',')
                .append(csv(detail.getAccount().getStatusType())).append(',')
                .append(csv(detail.getAccount().getCurrency())).append(',')
                .append(csv(detail.getBalance().getAvailableAmount())).append(',')
                .append(csv(detail.getBalance().getFrozenAmount())).append(',')
                .append(csv(detail.getBalance().getInTransitAmount())).append(',')
                .append(csv(detail.getBalance().getUpdatedAt())).append('\n');
        builder.append('\n');
        builder.append("最近流水\n");
        builder.append("流水号,业务类型,业务单号,方向,金额,变更前,变更后,状态,状态样式,操作人,时间\n");
        for (AccountLedgerEntity ledger : ledgers) {
            LedgerItemDTO item = accountingMapper.toLedgerDTO(ledger);
            builder.append(csv(ledger.getLedgerNo())).append(',')
                    .append(csv(ledger.getBizType())).append(',')
                    .append(csv(ledger.getBizNo())).append(',')
                    .append(csv(ledger.getDirection())).append(',')
                    .append(csv(AccountingMemoryStore.formatAmount(ledger.getAmount()))).append(',')
                    .append(csv(AccountingMemoryStore.formatAmount(ledger.getBeforeBalance()))).append(',')
                    .append(csv(AccountingMemoryStore.formatAmount(ledger.getAfterBalance()))).append(',')
                    .append(csv(ledger.getLedgerStatus())).append(',')
                    .append(csv(item.getStatusType())).append(',')
                    .append(csv(ledger.getOperatorName())).append(',')
                    .append(csv(ledger.getCreatedAt())).append('\n');
        }
        return builder.toString().getBytes(StandardCharsets.UTF_8);
    }

    private List<AccountLedgerEntity> findLedgersByAccountNo(String accountNo) {
        return accountingMemoryStore.ledgers().stream()
                .filter(item -> accountNo.equals(item.getAccountNo()))
                .collect(Collectors.toList());
    }

    private String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"") || escaped.contains("\n")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }
}
