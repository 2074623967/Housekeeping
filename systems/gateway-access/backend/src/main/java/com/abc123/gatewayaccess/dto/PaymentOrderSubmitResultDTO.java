package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道明确受理后的支付提交事实。
 */
@Data
public class PaymentOrderSubmitResultDTO {

    private String channelTransactionNo;
    private String attemptStatus;
    private String attemptStatusType;
    private String channelReturnCode;
}
