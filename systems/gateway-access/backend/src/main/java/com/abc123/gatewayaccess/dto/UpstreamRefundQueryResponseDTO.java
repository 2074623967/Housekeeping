package com.abc123.gatewayaccess.dto;

import lombok.Data;

/**
 * 渠道集成服务的稳定退款查单响应。
 */
@Data
public class UpstreamRefundQueryResponseDTO {

    private String code;
    private String message;
    private RefundOrderQueryResultDTO data;
}
