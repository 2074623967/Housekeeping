package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 服务者结算单列表行模型。
 */
@Data
public class WorkerSettlementListItemDTO {

    /** 结算单号。 */
    private String settlementOrderId;
    /** 服务者名称。 */
    private String workerName;
    /** 结算周期。 */
    private String period;
    /** 应结金额。 */
    private String amountShouldSettle;
    /** 扣减金额。 */
    private String deductAmount;
    /** 实结金额。 */
    private String amountNetSettle;
    /** 保证金影响金额。 */
    private String depositImpactAmount;
    /** 结算状态。 */
    private String status;
    /** 结算状态样式。 */
    private String statusType;
    /** 出款状态。 */
    private String payoutStatus;
    /** 出款状态样式。 */
    private String payoutStatusType;
}
