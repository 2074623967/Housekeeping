package com.abc123.hsp.entity;

import lombok.Data;

/** 支付控制策略不可变版本实体。 */
@Data
public class PaymentControlPolicyVersionEntity {

    private String sourceAppId;
    private Integer versionNo;
    private String sourceAppName;
    private String allowedPaymentMethods;
    private String allowedChannelCodes;
    private String allowedMerchantNos;
    private Integer minuteSubmitLimit;
    private Integer interfaceMinuteSubmitLimit;
    private String tokenAuthRequired;
    private String accessTokenValue;
    private String strictMode;
    private String status;
    private String statusType;
    private String changeType;
    private Integer sourceVersionNo;
    private String changeReason;
}
