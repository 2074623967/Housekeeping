package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 单次退款渠道尝试事实。
 */
@Data
public class RefundAttemptItemDTO {

    /** 退款尝试号，同时作为渠道侧商户退款请求号。 */
    private String refundAttemptNo;
    /** 渠道编码。 */
    private String channelCode;
    /** 渠道侧退款单号。 */
    private String channelRefundNo;
    /** 尝试状态。 */
    private String attemptStatus;
    /** 尝试状态样式。 */
    private String attemptStatusType;
    /** 首次进入渠道调用的时间。 */
    private String submittedAt;
    /** 尝试形成终态的时间。 */
    private String completedAt;
    /** 尝试创建时间。 */
    private String createdAt;
}
