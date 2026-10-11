package com.abc123.settlement.dto;

import lombok.Data;

/**
 * 结算单展示对象。
 */
@Data
public class SettlementOrderDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    private String settlementNo;
    private String batchNo;
    private String targetType;
    private String targetNo;
    private String targetName;
    private String shouldSettleAmount;
    private String deductAmount;
    private String netSettleAmount;
    private String settlementStatus;
    private String settlementStatusType;
    private String payoutStatus;
    private String payoutStatusType;
    private String auditStatus;
    private String auditStatusType;
    private String createdAt;
    private String clearingNo;
    /** 支付业务场景。 */
    private String payScene;
}
