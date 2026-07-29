package com.abc123.clearing.service.impl;

import com.abc123.clearing.dto.ClearingBatchDTO;
import com.abc123.clearing.dto.ClearingEventDTO;
import com.abc123.clearing.dto.ClearingOrderDTO;
import com.abc123.clearing.dto.ClearingOrderDetailDTO;
import com.abc123.clearing.dto.ClearingRuleDTO;
import com.abc123.clearing.dto.FeeRuleDTO;
import com.abc123.clearing.dto.ShareItemDTO;
import com.abc123.clearing.entity.ClearingBatchEntity;
import com.abc123.clearing.entity.ClearingEventEntity;
import com.abc123.clearing.entity.ClearingOrderEntity;
import com.abc123.clearing.entity.ClearingRuleEntity;
import com.abc123.clearing.entity.FeeRuleEntity;
import com.abc123.clearing.entity.ShareItemEntity;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.List;
import org.springframework.stereotype.Component;

/**
 * 清分系统对象映射器。
 */
@Component
public class ClearingMapper {

    private static final DecimalFormat AMOUNT_FORMAT = new DecimalFormat("0.00");

    public ClearingBatchDTO toBatchDTO(ClearingBatchEntity entity) {
        ClearingBatchDTO dto = new ClearingBatchDTO();
        dto.setBatchNo(entity.getBatchNo());
        dto.setBatchDate(entity.getBatchDate());
        dto.setSourceType(entity.getSourceType());
        dto.setBatchStatus(entity.getBatchStatus());
        dto.setBatchStatusType(statusType(entity.getBatchStatus()));
        dto.setTotalOrderCount(entity.getTotalOrderCount());
        dto.setSuccessOrderCount(entity.getSuccessOrderCount());
        dto.setFailedOrderCount(entity.getFailedOrderCount());
        dto.setTotalAmount(formatAmount(entity.getTotalAmount()));
        dto.setVersionNo(entity.getVersionNo());
        dto.setCreatedBy(entity.getCreatedBy());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setFinishedAt(entity.getFinishedAt());
        return dto;
    }

    public ClearingOrderDTO toOrderDTO(ClearingOrderEntity entity) {
        ClearingOrderDTO dto = new ClearingOrderDTO();
        dto.setClearingNo(entity.getClearingNo());
        dto.setBatchNo(entity.getBatchNo());
        dto.setPaymentOrderId(entity.getPaymentOrderId());
        dto.setOrderNo(entity.getOrderNo());
        dto.setOrderAmount(formatAmount(entity.getOrderAmount()));
        dto.setMerchantAmount(formatAmount(entity.getMerchantAmount()));
        dto.setWorkerAmount(formatAmount(entity.getWorkerAmount()));
        dto.setPlatformAmount(formatAmount(entity.getPlatformAmount()));
        dto.setChannelFeeAmount(formatAmount(entity.getChannelFeeAmount()));
        dto.setSubsidyAmount(formatAmount(entity.getSubsidyAmount()));
        dto.setClearingStatus(entity.getClearingStatus());
        dto.setClearingStatusType(statusType(entity.getClearingStatus()));
        dto.setRuleNo(entity.getRuleNo());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public ClearingOrderDetailDTO toOrderDetailDTO(ClearingOrderEntity entity, List<ShareItemDTO> shareItems, List<FeeRuleDTO> feeRules) {
        ClearingOrderDetailDTO dto = new ClearingOrderDetailDTO();
        dto.setOrder(toOrderDTO(entity));
        dto.setShareItems(shareItems);
        dto.setFeeRules(feeRules);
        return dto;
    }

    public ClearingRuleDTO toRuleDTO(ClearingRuleEntity entity) {
        ClearingRuleDTO dto = new ClearingRuleDTO();
        dto.setRuleNo(entity.getRuleNo());
        dto.setRuleName(entity.getRuleName());
        dto.setRuleType(entity.getRuleType());
        dto.setRuleExpression(entity.getRuleExpression());
        dto.setRuleStatus(entity.getRuleStatus());
        dto.setRuleStatusType(statusType(entity.getRuleStatus()));
        dto.setVersionNo(entity.getVersionNo());
        dto.setGreyFlag(entity.getGreyFlag());
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public FeeRuleDTO toFeeRuleDTO(FeeRuleEntity entity) {
        FeeRuleDTO dto = new FeeRuleDTO();
        dto.setFeeRuleNo(entity.getFeeRuleNo());
        dto.setFeeName(entity.getFeeName());
        dto.setFeeType(entity.getFeeType());
        dto.setFeeMode(entity.getFeeMode());
        dto.setFeeRate(entity.getFeeRate() == null ? "-" : entity.getFeeRate().stripTrailingZeros().toPlainString());
        dto.setFixedAmount(formatAmount(entity.getFixedAmount()));
        dto.setFeeBearer(entity.getFeeBearer());
        dto.setStatus(entity.getStatus());
        dto.setStatusType(statusType(entity.getStatus()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public ShareItemDTO toShareItemDTO(ShareItemEntity entity) {
        ShareItemDTO dto = new ShareItemDTO();
        dto.setShareItemNo(entity.getShareItemNo());
        dto.setClearingNo(entity.getClearingNo());
        dto.setShareType(entity.getShareType());
        dto.setShareTargetNo(entity.getShareTargetNo());
        dto.setShareTargetName(entity.getShareTargetName());
        dto.setShareAmount(formatAmount(entity.getShareAmount()));
        dto.setShareStatus(entity.getShareStatus());
        dto.setStatusType(statusType(entity.getShareStatus()));
        dto.setCreatedAt(entity.getCreatedAt());
        return dto;
    }

    public ClearingEventDTO toEventDTO(ClearingEventEntity entity) {
        ClearingEventDTO dto = new ClearingEventDTO();
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

    private String formatAmount(BigDecimal amount) {
        return amount == null ? "¥0.00" : "¥" + AMOUNT_FORMAT.format(amount);
    }

    private String statusType(String status) {
        if (status == null) {
            return "info";
        }
        if (status.contains("成功") || status.contains("启用") || status.contains("完成") || status.contains("已消费")) {
            return "success";
        }
        if (status.contains("失败") || status.contains("停用")) {
            return "danger";
        }
        if (status.contains("待") || status.contains("进行")) {
            return "warn";
        }
        return "info";
    }
}
