package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 退款渠道返回的确定性退款事实。
 */
@Data
public class RefundOrderQueryResultDTO {

    private String refundOrderId;
    private String refundAttemptNo;
    private String refundStatus;
    private String channelRefundNo;
    private String querySource;
}
