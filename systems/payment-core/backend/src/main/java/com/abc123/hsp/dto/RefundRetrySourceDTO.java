package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 失败退款重试前的额度校验快照。
 */
@Data
public class RefundRetrySourceDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 当前退款状态。 */
    private String status;
    /** 当前退款状态样式。 */
    private String statusType;
    /** 已受理退款渠道。 */
    private String channelCode;
    /** 渠道侧退款单号。 */
    private String channelRefundNo;
    /** 渠道受理时间。 */
    private String channelSubmittedAt;
    /** 退款申请时间。 */
    private String appliedAt;
    /** 退款成功时间。 */
    private String successAt;
    /** 退款终态时间。 */
    private String terminalAt;
    /** 最新退款尝试状态。 */
    private String latestAttemptStatus;
    private String latestAttemptStatusType;
    private String latestAttemptChannelCode;
    private String latestAttemptChannelRefundNo;
    /** 最新退款尝试完成时间。 */
    private String latestAttemptCompletedAt;
}
