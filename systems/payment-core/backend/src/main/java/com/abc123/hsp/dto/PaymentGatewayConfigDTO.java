package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付网关接入配置展示对象。
 */
@Data
public class PaymentGatewayConfigDTO {

    /** 网关编码。 */
    private String gatewayCode;
    /** 网关名称。 */
    private String gatewayName;
    /** 接入模式。 */
    private String accessMode;
    /** 适用渠道范围。 */
    private String channelScope;
    /** 环境范围。 */
    private String environmentScope;
    /** 网关基础地址。 */
    private String apiBaseUrl;
    /** 报文协议。 */
    private String protocolType;
    /** 签名算法。 */
    private String signAlgorithm;
    /** 证书别名。 */
    private String certificateAlias;
    /** 证书状态。 */
    private String certificateStatus;
    /** 证书状态样式。 */
    private String certificateStatusType;
    /** 发布阶段。 */
    private String releaseStage;
    /** 灰度策略。 */
    private String grayStrategy;
    /** 回调白名单。 */
    private String callbackWhitelist;
    /** 适配器编排。 */
    private String adapterRegistry;
    /** 超时时间。 */
    private String timeoutMs;
    /** 重试策略。 */
    private String retryPolicy;
    /** 网关状态。 */
    private String status;
    /** 网关状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
