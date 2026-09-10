package com.abc123.gatewayaccess.service;

import com.abc123.gatewayaccess.dto.RefundOrderSubmitRequestDTO;
import com.abc123.gatewayaccess.dto.RefundOrderSubmitResultDTO;

/**
 * 退款订单真实渠道提交代理服务。
 */
public interface RefundOrderSubmitService {

    RefundOrderSubmitResultDTO submit(String appCode, String accessKey, String clientIp,
                                      RefundOrderSubmitRequestDTO request);
}
