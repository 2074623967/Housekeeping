package com.abc123.refund.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 发起退款请求。
 */
@Data
public class RefundApplyRequestDTO {

    /** 原支付单号。 */
    private String paymentOrderId;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式：ORIGINAL、TRANSFER。 */
    private String refundMethod;
    /** 退款原因。 */
    private String refundReason;
    /** 幂等键。 */
    private String idempotencyKey;
}

