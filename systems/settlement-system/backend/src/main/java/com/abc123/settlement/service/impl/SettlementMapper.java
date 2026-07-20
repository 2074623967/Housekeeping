package com.abc123.settlement.service.impl;

import com.abc123.settlement.dto.ClearingGeneratedEventRequestDTO;
import com.abc123.settlement.dto.PayoutBatchDTO;
import com.abc123.settlement.dto.PayoutRecordDTO;
import com.abc123.settlement.dto.SettlementAuditLogDTO;
import com.abc123.settlement.dto.SettlementBatchDTO;
import com.abc123.settlement.dto.SettlementEventDTO;
import com.abc123.settlement.dto.SettlementItemDTO;
import com.abc123.settlement.dto.SettlementOrderDTO;
import com.abc123.settlement.dto.SettlementOrderDetailDTO;
import com.abc123.settlement.entity.PayoutBatchEntity;
import com.abc123.settlement.entity.PayoutRecordEntity;
import com.abc123.settlement.entity.SettlementAuditLogEntity;
import com.abc123.settlement.entity.SettlementBatchEntity;
import com.abc123.settlement.entity.SettlementEventEntity;
import com.abc123.settlement.entity.SettlementItemEntity;
import com.abc123.settlement.entity.SettlementOrderEntity;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 结算系统对象映射器。
 */
@Component
public class SettlementMapper {

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    public SettlementBatchDTO toBatchDTO(SettlementBatchEntity entity) {
        SettlementBatchDTO dto = new SettlementBatchDTO();
        dto.setBatchNo(entity.getBatchNo());
        dto.setBatchDate(entity.getBatchDate());
        dto.setSettlementType(entity.getSettlementType());
        dto.setBatchStatus(entity.getBatchStatus());
        dto.setBatchStatusType(statusType(entity.getBatchStatus()));
        dto.setTotalCount(entity.getTotalCount());
        dto.setAuditedCount(entity.getAuditedCount());
        dto.setPayoutCount(entity.getPayoutCount());
        dto.setTotalAmount(formatAmount(entity.getTotalAmount()));
        dto.setPayoutChannel(entity.getPayoutChannel());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        return dto;
    }

    public SettlementOrderDTO toOrderDTO(SettlementOrderEntity entity) {
        SettlementOrderDTO dto = new SettlementOrderDTO();
        dto.setSettlementNo(entity.getSettlementNo());
        dto.setBatchNo(entity.getBatchNo());
        dto.setTargetType(entity.getTargetType());
        dto.setTargetNo(entity.getTargetNo());
        dto.setTargetName(entity.getTargetName());
        dto.setShouldSettleAmount(formatAmount(entity.getShouldSettleAmount()));
        dto.setDeductAmount(formatAmount(entity.getDeductAmount()));
        dto.setNetSettleAmount(formatAmount(entity.getNetSettleAmount()));
        dto.setSettlementStatus(entity.getSettlementStatus());
        dto.setSettlementStatusType(statusType(entity.getSettlementStatus()));
        dto.setPayoutStatus(entity.getPayoutStatus());
        dto.setPayoutStatusType(statusType(entity.getPayoutStatus()));
        dto.setAuditStatus(entity.getAuditStatus());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public SettlementOrderDetailDTO toOrderDetailDTO(SettlementOrderEntity entity, List<SettlementItemDTO> items, List<SettlementAuditLogDTO> logs) {
        SettlementOrderDetailDTO dto = new SettlementOrderDetailDTO();
        dto.setOrder(toOrderDTO(entity));
        dto.setItems(items);
        dto.setAuditLogs(logs);
        return dto;
    }

    public SettlementItemDTO toItemDTO(SettlementItemEntity entity) {
        SettlementItemDTO dto = new SettlementItemDTO();
        dto.setItemNo(entity.getItemNo());
        dto.setSettlementNo(entity.getSettlementNo());
        dto.setItemType(entity.getItemType());
        dto.setItemName(entity.getItemName());
        dto.setItemAmount(formatAmount(entity.getItemAmount()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public SettlementAuditLogDTO toAuditDTO(SettlementAuditLogEntity entity) {
        SettlementAuditLogDTO dto = new SettlementAuditLogDTO();
        dto.setAuditNo(entity.getAuditNo());
        dto.setSettlementNo(entity.getSettlementNo());
        dto.setAuditAction(entity.getAuditAction());
        dto.setAuditResult(entity.getAuditResult());
        dto.setOperatorName(entity.getOperatorName());
        dto.setRemark(entity.getRemark());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public PayoutBatchDTO toPayoutBatchDTO(PayoutBatchEntity entity) {
        PayoutBatchDTO dto = new PayoutBatchDTO();
        dto.setPayoutBatchNo(entity.getPayoutBatchNo());
        dto.setBatchNo(entity.getBatchNo());
        dto.setPayoutChannel(entity.getPayoutChannel());
        dto.setPayoutStatus(entity.getPayoutStatus());
        dto.setPayoutStatusType(statusType(entity.getPayoutStatus()));
        dto.setPayoutCount(entity.getPayoutCount());
        dto.setSuccessCount(entity.getSuccessCount());
        dto.setFailedCount(entity.getFailedCount());
        dto.setTotalAmount(formatAmount(entity.getTotalAmount()));
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        return dto;
    }

    public PayoutRecordDTO toPayoutRecordDTO(PayoutRecordEntity entity) {
        PayoutRecordDTO dto = new PayoutRecordDTO();
        dto.setPayoutNo(entity.getPayoutNo());
        dto.setPayoutBatchNo(entity.getPayoutBatchNo());
        dto.setSettlementNo(entity.getSettlementNo());
        dto.setTargetNo(entity.getTargetNo());
        dto.setTargetName(entity.getTargetName());
        dto.setPayoutAmount(formatAmount(entity.getPayoutAmount()));
        dto.setPayoutStatus(entity.getPayoutStatus());
        dto.setPayoutStatusType(statusType(entity.getPayoutStatus()));
        dto.setRetryCount(String.valueOf(entity.getRetryCount()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public SettlementEventDTO toEventDTO(SettlementEventEntity entity) {
        SettlementEventDTO dto = new SettlementEventDTO();
        dto.setEventNo(entity.getEventNo());
        dto.setEventType(entity.getEventType());
        dto.setBizNo(entity.getBizNo());
        dto.setSummary(entity.getSummary());
        dto.setPayload(entity.getPayload());
        dto.setEventStatus(entity.getEventStatus());
        dto.setStatusType(statusType(entity.getEventStatus()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public String formatAmount(BigDecimal amount) {
        return amount == null ? "¥0.00" : "¥" + AMOUNT_FORMAT.format(amount);
    }

    private String statusType(String status) {
        if (status == null) {
            return "info";
        }
        if (status.contains("成功") || status.contains("已完成") || status.contains("通过") || status.contains("已发放")) {
            return "success";
        }
        if (status.contains("失败") || status.contains("退回") || status.contains("驳回") || status.contains("停用")) {
            return "danger";
        }
        if (status.contains("待") || status.contains("处理中") || status.contains("审核")) {
            return "warn";
        }
        return "info";
    }
}
