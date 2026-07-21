package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付渠道配置展示项。
 */
@Data
public class PaymentChannelConfigDTO {

    /** 支付渠道编码。 */
    private String channelCode;
    /** 支付渠道名称。 */
    private String channelName;
    /** 支付方式。 */
    private String paymentMethod;
    /** 渠道商户号。 */
    private String merchantNo;
    /** 商户应用标识。 */
    private String merchantAppId;
    /** 渠道回调通知地址。 */
    private String callbackNotifyUrl;
    /** 验签密钥配置情况。 */
    private String callbackSecretMasked;
    /** 证书档案。 */
    private String certificateProfile;
    /** 回调验签时间窗。 */
    private String notifySignWindow;
    /** 退款时效。 */
    private String refundWindow;
    /** 风控标签。 */
    private String riskControlTag;
    /** 适用场景范围。 */
    private String sceneScope;
    /** 渠道状态。 */
    private String status;
    /** 状态样式类型。 */
    private String statusType;
    /** 单日限额展示值。 */
    private String dailyLimit;
    /** 渠道优先级。 */
    private Integer priority;
    /** 更新时间。 */
    private String updatedAt;
}
