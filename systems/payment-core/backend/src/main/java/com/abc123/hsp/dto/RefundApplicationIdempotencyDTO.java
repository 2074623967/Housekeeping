package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 已落库退款申请的幂等比对快照。
 */
@Data
public class RefundApplicationIdempotencyDTO {

    private String refundOrderId;
    private String paymentOrderId;
    private BigDecimal refundAmount;
    private String refundMethod;
    private String refundReason;
    private String operator;
}
