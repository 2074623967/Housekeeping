package com.abc123.accounting.service.impl;

import com.abc123.accounting.dto.AccountDetailDTO;
import com.abc123.accounting.dto.AccountEventDTO;
import com.abc123.accounting.dto.AccountListItemDTO;
import com.abc123.accounting.dto.AccountSubjectDTO;
import com.abc123.accounting.dto.AdjustmentItemDTO;
import com.abc123.accounting.dto.BalanceSnapshotDTO;
import com.abc123.accounting.dto.FreezeItemDTO;
import com.abc123.accounting.dto.LedgerItemDTO;
import com.abc123.accounting.entity.AccountAdjustmentEntity;
import com.abc123.accounting.entity.AccountBalanceEntity;
import com.abc123.accounting.entity.AccountEntity;
import com.abc123.accounting.entity.AccountEventEntity;
import com.abc123.accounting.entity.AccountFreezeEntity;
import com.abc123.accounting.entity.AccountLedgerEntity;
import com.abc123.accounting.entity.AccountSubjectEntity;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * 第二阶段首版对象映射器。
 */
@Component
public class AccountingMapper {

    public AccountSubjectDTO toSubjectDTO(AccountSubjectEntity entity, int linkedAccountCount) {
        AccountSubjectDTO dto = new AccountSubjectDTO();
        dto.setSubjectId(entity.getSubjectId());
        dto.setSubjectType(entity.getSubjectType());
        dto.setSubjectName(entity.getSubjectName());
        dto.setOwnerName(entity.getOwnerName());
        dto.setStatus(entity.getStatus());
        dto.setStatusType("启用".equals(entity.getStatus()) ? "success" : "danger");
        dto.setLinkedAccountCount(String.valueOf(linkedAccountCount));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public AccountListItemDTO toAccountDTO(AccountEntity entity, AccountBalanceEntity balance) {
        AccountListItemDTO dto = new AccountListItemDTO();
        dto.setAccountNo(entity.getAccountNo());
        dto.setSubjectId(entity.getSubjectId());
        dto.setSubjectName(entity.getSubjectName());
        dto.setAccountType(entity.getAccountType());
        dto.setAccountStatus(entity.getAccountStatus());
        dto.setStatusType("正常".equals(entity.getAccountStatus()) ? "success" : "warn");
        dto.setCurrency(entity.getCurrency());
        dto.setAvailableAmount(AccountingMemoryStore.formatAmount(balance.getAvailableAmount()));
        dto.setFrozenAmount(AccountingMemoryStore.formatAmount(balance.getFrozenAmount()));
        dto.setInTransitAmount(AccountingMemoryStore.formatAmount(balance.getInTransitAmount()));
        dto.setLastChangeAt(entity.getLastChangeAt());
        return dto;
    }

    public BalanceSnapshotDTO toBalanceDTO(AccountEntity entity, AccountBalanceEntity balance) {
        BalanceSnapshotDTO dto = new BalanceSnapshotDTO();
        dto.setAccountNo(entity.getAccountNo());
        dto.setSubjectName(entity.getSubjectName());
        dto.setAccountType(entity.getAccountType());
        dto.setAccountStatus(entity.getAccountStatus());
        dto.setAvailableAmount(AccountingMemoryStore.formatAmount(balance.getAvailableAmount()));
        dto.setFrozenAmount(AccountingMemoryStore.formatAmount(balance.getFrozenAmount()));
        dto.setInTransitAmount(AccountingMemoryStore.formatAmount(balance.getInTransitAmount()));
        dto.setUpdatedAt(balance.getUpdatedAt());
        return dto;
    }

    public LedgerItemDTO toLedgerDTO(AccountLedgerEntity entity) {
        LedgerItemDTO dto = new LedgerItemDTO();
        dto.setLedgerNo(entity.getLedgerNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizType(entity.getBizType());
        dto.setBizNo(entity.getBizNo());
        dto.setDirection(entity.getDirection());
        dto.setAmount(AccountingMemoryStore.formatAmount(entity.getAmount()));
        dto.setBeforeBalance(AccountingMemoryStore.formatAmount(entity.getBeforeBalance()));
        dto.setAfterBalance(AccountingMemoryStore.formatAmount(entity.getAfterBalance()));
        dto.setLedgerStatus(entity.getLedgerStatus());
        dto.setStatusType("已记账".equals(entity.getLedgerStatus()) ? "success" : "warn");
        dto.setOperatorName(entity.getOperatorName());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public FreezeItemDTO toFreezeDTO(AccountFreezeEntity entity) {
        FreezeItemDTO dto = new FreezeItemDTO();
        dto.setFreezeNo(entity.getFreezeNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setBizNo(entity.getBizNo());
        dto.setFreezeType(entity.getFreezeType());
        dto.setFreezeReason(entity.getFreezeReason());
        dto.setFreezeAmount(AccountingMemoryStore.formatAmount(entity.getFreezeAmount()));
        dto.setFreezeStatus(entity.getFreezeStatus());
        dto.setStatusType("已解冻".equals(entity.getFreezeStatus()) ? "info" : "warn");
        dto.setOperatorName(entity.getOperatorName());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUnfrozenAt(entity.getUnfrozenAt());
        return dto;
    }

    public AdjustmentItemDTO toAdjustmentDTO(AccountAdjustmentEntity entity) {
        AdjustmentItemDTO dto = new AdjustmentItemDTO();
        dto.setAdjustNo(entity.getAdjustNo());
        dto.setAccountNo(entity.getAccountNo());
        dto.setAdjustDirection(entity.getAdjustDirection());
        dto.setAdjustAmount(AccountingMemoryStore.formatAmount(entity.getAdjustAmount()));
        dto.setAdjustReason(entity.getAdjustReason());
        dto.setAdjustStatus(entity.getAdjustStatus());
        dto.setStatusType("已生效".equals(entity.getAdjustStatus()) ? "success" : "warn");
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setApprovedBy(entity.getApprovedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setApprovedAt(entity.getApprovedAt());
        return dto;
    }

    public AccountEventDTO toEventDTO(AccountEventEntity entity) {
        AccountEventDTO dto = new AccountEventDTO();
        dto.setEventNo(entity.getEventNo());
        dto.setEventType(entity.getEventType());
        dto.setBizNo(entity.getBizNo());
        dto.setEventStatus(entity.getEventStatus());
        dto.setStatusType("已消费".equals(entity.getEventStatus()) ? "success" : "info");
        dto.setSummary(entity.getSummary());
        dto.setPayload(entity.getPayload());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public AccountDetailDTO toAccountDetail(AccountEntity account, AccountBalanceEntity balance,
            List<AccountLedgerEntity> recentLedgers) {
        AccountDetailDTO dto = new AccountDetailDTO();
        dto.setAccount(toAccountDTO(account, balance));
        dto.setBalance(toBalanceDTO(account, balance));
        dto.setRecentLedgers(recentLedgers.stream().limit(8).map(this::toLedgerDTO).collect(Collectors.toList()));
        return dto;
    }

    public List<LedgerItemDTO> toLedgerDTOs(List<AccountLedgerEntity> entities) {
        List<LedgerItemDTO> items = new ArrayList<>();
        for (AccountLedgerEntity entity : entities) {
            items.add(toLedgerDTO(entity));
        }
        return items;
    }
}
