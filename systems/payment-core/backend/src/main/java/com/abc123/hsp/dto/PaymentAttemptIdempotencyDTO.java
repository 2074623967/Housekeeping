package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 已存在支付尝试的幂等归属。
 */
@Data
public class PaymentAttemptIdempotencyDTO {

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 支付单号。 */
    private String paymentOrderId;
    private String requestFingerprint;
}
