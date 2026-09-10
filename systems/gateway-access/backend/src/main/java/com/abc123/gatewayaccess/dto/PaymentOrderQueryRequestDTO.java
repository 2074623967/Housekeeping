package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 支付订单主动查单请求。
 */
@Data
public class PaymentOrderQueryRequestDTO {

    private String paymentOrderId;
    private String orderNo;
    private String channelCode;
    private String channelTransactionNo;
}
