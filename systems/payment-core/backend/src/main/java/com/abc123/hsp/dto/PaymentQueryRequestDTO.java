package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付查单请求。
 */
@Data
public class PaymentQueryRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 后台或任务发起来源；公共自助入口传入值不会被采信。 */
    private String operator;
}
