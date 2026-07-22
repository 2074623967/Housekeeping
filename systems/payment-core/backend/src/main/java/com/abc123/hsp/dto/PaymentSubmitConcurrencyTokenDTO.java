package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付提交并发令牌对象。
 */
@Data
public class PaymentSubmitConcurrencyTokenDTO {

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 来源应用标识。 */
    private String sourceAppId;
    /** 令牌状态。 */
    private String tokenStatus;
    /** 当前占用幂等键。 */
    private String holderIdempotencyKey;
    /** 当前占用终端。 */
    private String holderTerminal;
    /** 当前占用客户端IP。 */
    private String holderClientIp;
    /** 令牌过期时间。 */
    private String expireAt;
}
