package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.dto.PageResultDTO;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.service.LedgerService;
import java.nio.charset.StandardCharsets;
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

    @Override
    public byte[] exportSnapshot(String accountNo, String bizNo, String bizType) {
        String normalizedBizNo = bizNo == null ? "" : bizNo.toLowerCase(Locale.ROOT);
        List<AccountLedgerEntity> records = accountingMemoryStore.ledgers().stream()
                .filter(item -> accountNo == null || accountNo.isEmpty() || accountNo.equals(item.getAccountNo()))
                .filter(item -> normalizedBizNo.isEmpty() || item.getBizNo().toLowerCase(Locale.ROOT).contains(normalizedBizNo))
                .filter(item -> bizType == null || bizType.isEmpty() || bizType.equals(item.getBizType()))
                .collect(Collectors.toList());
        StringBuilder builder = new StringBuilder();
        builder.append('\uFEFF');
        builder.append("流水号,账户号,业务类型,业务单号,方向,金额,前余额,后余额,流水状态,状态样式,操作人,创建时间\n");
        for (AccountLedgerEntity ledger : records) {
            LedgerItemDTO item = accountingMapper.toLedgerDTO(ledger);
            builder.append(csv(ledger.getLedgerNo())).append(',')
                    .append(csv(ledger.getAccountNo())).append(',')
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
