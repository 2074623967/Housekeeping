package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 退款订单主动查单请求。
 */
@Data
public class RefundOrderQueryRequestDTO {

    private String refundOrderId;
    private String refundAttemptNo;
    private String channelCode;
    private String channelRefundNo;
}
