package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付控制策略展示与运行时配置对象。
 */
@Data
public class PaymentControlPolicyDTO {

    /** 来源应用标识。 */
    private String sourceAppId;
    /** 来源应用名称。 */
    private String sourceAppName;
    /** 允许支付方式。 */
    private String allowedPaymentMethods;
    /** 允许渠道。 */
    private String allowedChannelCodes;
    /** 分钟级限流阈值。 */
    private Integer minuteSubmitLimit;
    /** 严格控制模式。 */
    private String strictMode;
    /** 自检状态。 */
    private String selfCheckStatus;
    /** 自检状态样式。 */
    private String selfCheckStatusType;
    /** 自检提示。 */
    private String selfCheckMessage;
    /** 生效状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}
