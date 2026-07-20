package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentRouteContextDTO;
import com.abc123.hsp.dto.PaymentRouteDecisionDTO;

/**
 * 支付渠道路由服务。
 */
public interface PaymentChannelRoutingService {

    /**
     * 根据支付上下文解析标准渠道编码和路由结果。
     *
     * @param routeContext 路由上下文
     * @return 路由决策结果
     */
    PaymentRouteDecisionDTO resolve(PaymentRouteContextDTO routeContext);
}
