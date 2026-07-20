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
