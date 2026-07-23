package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付查单请求。
 */
@Data
public class PaymentQueryRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
}
