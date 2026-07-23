package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付关闭请求。
 */
@Data
public class PaymentCloseRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
}
