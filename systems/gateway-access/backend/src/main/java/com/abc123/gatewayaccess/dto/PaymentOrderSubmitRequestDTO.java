package com.abc123.gatewayaccess.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付订单渠道提交请求。
 */
@Data
public class PaymentOrderSubmitRequestDTO {

    private String paymentOrderId;
    private String prepayOrderNo;
    private String orderNo;
    private String payScene;
    private String customerName;
    private BigDecimal amount;
    private String paymentMethod;
    private String sourceAppId;
    private String requestedChannelCode;
    private String resolvedChannelCode;
    private String terminal;
    private String clientIp;
    private String idempotencyKey;
}
