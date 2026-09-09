package com.abc123.hsp.entity;

import lombok.Data;

/** 异常告警通知供应商运行配置实体。 */
@Data
public class PaymentAlertProviderConfigEntity {

    private String providerCode;
    private String providerName;
    private String channelCode;
    private String endpointAlias;
    private String templateCode;
    private String templateBody;
    private String routeRule;
    private Integer routePriority;
    private String retryPolicy;
    private String rateLimitPolicy;
    private String status;
    private String statusType;
}
