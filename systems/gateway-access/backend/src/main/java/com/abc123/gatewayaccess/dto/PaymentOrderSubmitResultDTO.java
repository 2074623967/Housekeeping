package com.abc123.gatewayaccess.dto;

import java.util.Map;
import lombok.Data;

/**
 * 渠道明确受理后的支付提交事实。
 */
@Data
public class PaymentOrderSubmitResultDTO {

    /**
     * 原生终端支付参数，仅在小程序渠道已完成签名且允许直接交给客户端时返回；不落库。
     */
    private Map<String, String> nativePaymentParams;

    private String channelTransactionNo;
    private String gatewayCode;
    private String attemptStatus;
    private String attemptStatusType;
    private String channelReturnCode;
}
