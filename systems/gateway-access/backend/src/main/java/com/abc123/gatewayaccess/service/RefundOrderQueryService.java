package com.abc123.gatewayaccess.service;

import com.abc123.gatewayaccess.dto.RefundOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderQueryResultDTO;

/**
 * 退款订单主动查单代理服务。
 */
public interface RefundOrderQueryService {

    RefundOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                    RefundOrderQueryRequestDTO request);
}
