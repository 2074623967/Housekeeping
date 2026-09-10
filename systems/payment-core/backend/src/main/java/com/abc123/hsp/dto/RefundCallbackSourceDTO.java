package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款回调收口前的锁定快照。
 */
@Data
public class RefundCallbackSourceDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 当前退款状态。 */
    private String status;
    /** 已受理退款渠道。 */
    private String channelCode;
    /** 已受理渠道退款单号。 */
    private String channelRefundNo;
    /** 对应退款提交事件状态，用于识别可按平台退款单号核查的未知受理。 */
    private String submitEventStatus;
}
