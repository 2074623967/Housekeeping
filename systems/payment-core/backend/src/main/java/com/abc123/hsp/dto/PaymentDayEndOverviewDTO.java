package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付日终处理总览。
 */
@Data
public class PaymentDayEndOverviewDTO {

    /** 批次总数。 */
    private Integer totalBatchCount;
    /** 已完成批次数。 */
    private Integer completedBatchCount;
    /** 异常批次数。 */
    private Integer abnormalBatchCount;
    /** 最近业务日。 */
    private String latestBizDate;
    /** 最近批次状态。 */
    private String latestBatchStatus;
    /** 当前未收口渠道异常数。 */
    private Integer openChannelAbnormalCount;
    /** 当前未收口内部异常数。 */
    private Integer openInternalAbnormalCount;
    /** 当前待收口退款数。 */
    private Integer openPendingRefundCount;
    /** 最近批次列表。 */
    private List<PaymentDayEndBatchListItemDTO> recentBatches;
}
