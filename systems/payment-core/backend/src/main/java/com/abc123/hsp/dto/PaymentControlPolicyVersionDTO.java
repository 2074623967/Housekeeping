package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略不可变版本展示项，不暴露访问令牌原文。 */
@Data
public class PaymentControlPolicyVersionDTO {

    private String sourceAppId;
    private Integer versionNo;
    private String sourceAppName;
    private String allowedPaymentMethods;
    private String allowedChannelCodes;
    private String allowedMerchantNos;
    private Integer minuteSubmitLimit;
    private Integer interfaceMinuteSubmitLimit;
    private String tokenAuthRequired;
    private Boolean accessTokenConfigured;
    private String strictMode;
    private String status;
    private String statusType;
    private String changeType;
    private Integer sourceVersionNo;
    private String changeReason;
    private String createdAt;
}
