package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付配置中心总览数据。
 */
@Data
public class PaymentConfigOverviewDTO {

    /** 渠道配置列表。 */
    private List<PaymentChannelConfigDTO> channels;
    /** 路由规则列表。 */
    private List<PaymentRouteRuleConfigDTO> routeRules;
    /** 支付协议列表。 */
    private List<PaymentProtocolConfigDTO> protocols;
    /** 协议类型字典。 */
    private List<PaymentProtocolTypeOptionDTO> protocolTypeOptions;
    /** 渠道返回码映射列表。 */
    private List<PaymentChannelReturnCodeConfigDTO> returnCodeMappings;
    /** 支付网关接入配置列表。 */
    private List<PaymentGatewayConfigDTO> gateways;
    /** 支付控制策略列表。 */
    private List<PaymentControlPolicyDTO> controlPolicies;
}
