package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 服务者结算单实体，对应表：t_worker_settlement_order。
 */
@Data
public class WorkerSettlementOrderEntity {

    /** 服务者结算单号。 */
    private String settlementOrderId;
    /** 服务者名称。 */
    private String workerName;
    /** 结算账期。 */
    private String period;
    /** 应结金额。 */
    private BigDecimal amountShouldSettle;
    /** 扣减金额。 */
    private BigDecimal deductAmount;
    /** 实结金额。 */
    private BigDecimal amountNetSettle;
    /** 保证金影响金额。 */
    private BigDecimal depositImpactAmount;
    /** 结算单状态。 */
    private String status;
    /** 结算单状态样式类型。 */
    private String statusType;
    /** 出款状态。 */
    private String payoutStatus;
    /** 出款状态样式类型。 */
    private String payoutStatusType;
}
