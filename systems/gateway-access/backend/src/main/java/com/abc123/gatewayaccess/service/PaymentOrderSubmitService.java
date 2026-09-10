package com.abc123.gatewayaccess.service;

import com.abc123.gatewayaccess.dto.PaymentOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.PaymentOrderSubmitResultDTO;

/**
 * 支付订单真实渠道提交代理。
 */
public interface PaymentOrderSubmitService {

    PaymentOrderSubmitResultDTO submit(String appCode, String accessKey, String clientIp,
                                       PaymentOrderSubmitRequestDTO request);
}
