package com.abc123.hsp.entity;

import lombok.Data;

/** 支付控制策略运行配置实体。 */
@Data
public class PaymentControlPolicyEntity {

    private String sourceAppId;
    private String sourceAppName;
    private String allowedPaymentMethods;
    private String allowedChannelCodes;
    private String allowedMerchantNos;
    private Integer minuteSubmitLimit;
    private Integer interfaceMinuteSubmitLimit;
    private String tokenAuthRequired;
    private String accessTokenValue;
    private String strictMode;
    private String selfCheckStatus;
    private String selfCheckStatusType;
    private String selfCheckMessage;
    private String status;
    private String statusType;
}
