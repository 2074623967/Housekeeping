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
    /** 当前待收口支付数；历史未采集批次为 null。 */
    private Integer openPendingPaymentCount;
    /** 当前支付成功时间缺失数；历史未采集批次为 null。 */
    private Integer openMissingPaymentSuccessTimeCount;
    /** 当前支付成功事实不一致数；历史未采集批次为 null。 */
    private Integer openPaymentSuccessFactMismatchCount;
    /** 当前待收口退款数。 */
    private Integer openPendingRefundCount;
    /** 当前退款失败数；历史未采集批次为 null。 */
    private Integer openRefundFailCount;
    /** 当前退款成功时间缺失数；历史未采集批次为 null。 */
    private Integer openMissingRefundSuccessTimeCount;
    /** 当前退款终态事实不一致数；历史未采集批次为 null。 */
    private Integer openRefundTerminalFactMismatchCount;
    /** 最近批次渠道成功单量。 */
    private Integer latestChannelSuccessCount;
    /** 最近批次渠道成功金额。 */
    private String latestChannelSuccessAmount;
    /** 最近批次内部事件成功单量。 */
    private Integer latestInternalSuccessCount;
    /** 最近批次内部事件成功金额。 */
    private String latestInternalSuccessAmount;
    /** 最近批次支付成功差异单量。 */
    private Integer latestPaymentSuccessGapCount;
    /** 最近批次支付成功差异金额。 */
    private String latestPaymentSuccessGapAmount;
    /** 最近批次待收口支付金额；历史未采集批次为 null。 */
    private String latestPendingPaymentAmount;
    /** 最近批次待收口退款金额。 */
    private String latestPendingRefundAmount;
    /** 最近批次退款失败金额；历史未采集批次为 null。 */
    private String latestRefundFailAmount;
    /** 当前对账准入状态。 */
    private String reconciliationReadinessStatus;
    /** 当前对账准入状态样式。 */
    private String reconciliationReadinessType;
    /** 当前对账准入结论说明。 */
    private String reconciliationReadinessSummary;
    /** 当前对账准入建议动作。 */
    private String reconciliationSuggestedAction;
    /** 当前对账准入责任方。 */
    private String reconciliationBlockingOwner;
    /** 当前差异告警。 */
    private List<PaymentDayEndAlertItemDTO> alerts;
    /** 最近批次列表。 */
    private List<PaymentDayEndBatchListItemDTO> recentBatches;
}
