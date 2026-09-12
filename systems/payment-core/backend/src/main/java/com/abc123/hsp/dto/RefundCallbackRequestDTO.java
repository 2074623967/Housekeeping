package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道退款结果回调请求体。
 */
@Data
public class RefundCallbackRequestDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 本轮退款渠道尝试号。 */
    private String refundAttemptNo;
    /** 渠道侧退款单号。 */
    private String channelRefundNo;
    /** 渠道退款状态，仅支持 SUCCESS 或 FAIL。 */
    private String refundStatus;
    /** 渠道回调时间戳，Unix 秒级时间戳。 */
    private String timestamp;
    /** 渠道回调随机串。 */
    private String nonce;
    /** 渠道签名。 */
    private String signature;
}
