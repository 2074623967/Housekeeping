package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道回调请求体。
 */
@Data
public class PaymentCallbackRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 渠道侧交易号。 */
    private String channelTransactionNo;
    /** 渠道交易状态。 */
    private String tradeStatus;
    /** 渠道回调时间戳，Unix 秒级时间戳。 */
    private String timestamp;
    /** 渠道回调随机串。 */
    private String nonce;
    /** 渠道签名。 */
    private String signature;
}
