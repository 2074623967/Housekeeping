package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 服务者结算总览指标。
 */
@Data
public class WorkerSettlementOverviewDTO {

    /** 结算单总数。 */
    private Long totalSettlementCount;
    /** 待审核结算单数。 */
    private Long pendingAuditCount;
    /** 待出款结算单数。 */
    private Long payoutPendingCount;
    /** 出款中结算单数。 */
    private Long payingCount;
    /** 出款成功结算单数。 */
    private Long payoutSuccessCount;
    /** 实结金额合计。 */
    private String totalNetSettleAmount;
    /** 扣减金额合计。 */
    private String totalDeductAmount;
    /** 保证金影响合计。 */
    private String totalDepositImpactAmount;
    /** 净额为负的结算单数。 */
    private Integer negativeNetSettleCount;
}
