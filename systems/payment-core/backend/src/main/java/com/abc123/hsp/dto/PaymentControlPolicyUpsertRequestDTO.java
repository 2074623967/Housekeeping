package com.abc123.hsp.dto;

import lombok.Data;

/** 支付控制策略新增或编辑候选。 */
@Data
public class PaymentControlPolicyUpsertRequestDTO {

    private String sourceAppId;
    private String sourceAppName;
    private String allowedPaymentMethods;
    private String allowedChannelCodes;
    private String allowedMerchantNos;
    private Integer minuteSubmitLimit;
    private Integer interfaceMinuteSubmitLimit;
    private Boolean tokenAuthRequired;
    private String accessTokenValue;
    private Boolean strictMode;
    private Boolean enabled;
    private String changeReason;
}
