package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道明确受理后的退款提交事实。
 */
@Data
public class RefundOrderSubmitResultDTO {

    private String refundOrderId;
    private String refundAttemptNo;
    private String channelRefundNo;
    private String status;
    private String statusType;
}
