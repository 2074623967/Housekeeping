package com.abc123.gatewayaccess.service;

import com.abc123.gatewayaccess.dto.PaymentOrderQueryRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderQueryResultDTO;

/**
 * 支付订单真实渠道查单代理。
 */
public interface PaymentOrderQueryService {

    PaymentOrderQueryResultDTO query(String appCode, String accessKey, String clientIp,
                                     PaymentOrderQueryRequestDTO request);
}
