package com.abc123.hsp.dto;

import lombok.Data;

/** 异常告警通知供应商新增或编辑候选请求。 */
@Data
public class PaymentAlertProviderUpsertRequestDTO {

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
    private Boolean enabled;
    private String requester;
    private String changeReason;
}
