package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付关闭请求。
 */
@Data
public class PaymentCloseRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 后台发起操作人；公共自助入口传入值不会被采信。 */
    private String operator;
}
