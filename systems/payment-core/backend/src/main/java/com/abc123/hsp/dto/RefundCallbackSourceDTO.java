package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款回调收口前的锁定快照。
 */
@Data
public class RefundCallbackSourceDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 本次退款金额，用于终态金额事实校验。 */
    private BigDecimal refundAmount;
    /** 原支付金额，用于终态金额事实校验。 */
    private BigDecimal paidAmount;
    /** 与列表、日终共用的退款终态事实异常结果。 */
    private Boolean terminalFactMismatch;
    /** 当前退款尝试号。 */
    private String refundAttemptNo;
    /** 当前退款状态。 */
    private String status;
    private String statusType;
    /** 退款申请时间，用于校验终态事实时间线。 */
    private String appliedAt;
    /** 退款单成功时间，用于校验终态事实完整性。 */
    private String successAt;
    /** 退款单终态时间，用于校验成功和失败账期事实。 */
    private String terminalAt;
    /** 已受理退款渠道。 */
    private String channelCode;
    private String orderChannelCode;
    /** 已受理渠道退款单号。 */
    private String channelRefundNo;
    private String orderChannelRefundNo;
    /** 首次获得渠道受理号的时间，用于校验终态事实时间线。 */
    private String channelSubmittedAt;
    /** 对应退款提交事件状态，用于识别可按平台退款单号核查的未知受理。 */
    private String submitEventStatus;
    /** 当前退款尝试状态。 */
    private String attemptStatus;
    private String attemptStatusType;
    /** 当前退款尝试完成时间，用于校验终态事实完整性。 */
    private String attemptCompletedAt;
}
