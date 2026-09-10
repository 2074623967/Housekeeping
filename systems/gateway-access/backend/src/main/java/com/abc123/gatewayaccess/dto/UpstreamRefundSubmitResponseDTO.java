package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道集成服务的稳定退款提交响应。
 */
@Data
public class UpstreamRefundSubmitResponseDTO {

    private String code;
    private String message;
    private RefundOrderSubmitResultDTO data;
}
