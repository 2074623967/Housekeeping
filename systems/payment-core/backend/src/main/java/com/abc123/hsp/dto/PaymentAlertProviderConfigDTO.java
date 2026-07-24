package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 异常告警通知供应商配置。
 */
@Data
public class PaymentAlertProviderConfigDTO {

    /** 供应商配置编码。 */
    private String providerCode;
    /** 供应商名称。 */
    private String providerName;
    /** 通知通道。 */
    private String channelCode;
    /** 接入端点标识。 */
    private String endpointAlias;
    /** 模板编码。 */
    private String templateCode;
    /** 重试策略。 */
    private String retryPolicy;
    /** 限流策略。 */
    private String rateLimitPolicy;
    /** 配置状态。 */
    private String status;
    /** 配置状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
