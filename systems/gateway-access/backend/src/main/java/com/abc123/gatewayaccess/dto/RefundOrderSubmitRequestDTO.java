package com.abc123.gatewayaccess.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款订单渠道提交请求。
 */
@Data
public class RefundOrderSubmitRequestDTO {

    private String refundOrderId;
    private String refundAttemptNo;
    private String paymentOrderId;
    private String orderNo;
    private String customerName;
    private BigDecimal refundAmount;
    private String refundMethod;
    private String refundReason;
    private String channelCode;
}
