package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道集成服务的稳定查单响应。
 */
@Data
public class UpstreamPaymentQueryResponseDTO {

    private String code;
    private String message;
    private PaymentOrderQueryResultDTO data;
}
