package com.abc123.settlement.dto;

import java.util.List;
import lombok.Data;

/**
 * 结算工作台聚合视图。
 */
@Data
public class SettlementDashboardDTO {

    /** 结算批次数。 */
    private int batchCount;
    /** 结算单数。 */
    private int orderCount;
    /** 出款批次数。 */
    private int payoutCount;
    /** 结算事件数。 */
    private int eventCount;
    /** 待审核数。 */
    private int pendingAuditCount;
    /** 驳回待修正数。 */
    private int rejectedCount;
    /** 失败出款批次数。 */
    private int failedPayoutCount;
    /** 待处理事件数。 */
    private int pendingEventCount;
    /** 待出款草稿数。 */
    private int draftPayoutCount;
    /** 处理中出款批次数。 */
    private int processingPayoutCount;
    /** 最近批次。 */
    private List<SettlementBatchDTO> recentBatches;
    /** 最近结算单。 */
    private List<SettlementOrderDTO> recentOrders;
    /** 风险出款批次。 */
    private List<PayoutBatchDTO> riskPayouts;
    /** 最近事件。 */
    private List<SettlementEventDTO> recentEvents;
}
