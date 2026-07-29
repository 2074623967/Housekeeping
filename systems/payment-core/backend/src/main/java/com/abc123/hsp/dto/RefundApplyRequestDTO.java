package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 后台发起退款请求。
 */
@Data
public class RefundApplyRequestDTO {

    /** 原支付单号。 */
    private String paymentOrderId;
    /** 本次退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式，例如原路退款、线下打款、退转付。 */
    private String refundMethod;
    /** 退款原因，便于财务和运营复核。 */
    private String refundReason;
}
