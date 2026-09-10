package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道集成服务的稳定支付提交响应。
 */
@Data
public class UpstreamPaymentSubmitResponseDTO {

    private String code;
    private String message;
    private PaymentOrderSubmitResultDTO data;
}
