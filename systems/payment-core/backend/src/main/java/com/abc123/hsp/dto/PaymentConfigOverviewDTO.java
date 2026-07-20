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
}
