package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 支付渠道不可变版本展示项，不暴露回调密钥或公钥原文。 */
@Data
public class PaymentChannelVersionDTO {

    private String channelCode;
    private Integer versionNo;
    private String channelName;
    private String paymentMethod;
    private String merchantNo;
    private String merchantAppId;
    private Boolean callbackSecretConfigured;
    private String callbackSignAlgorithm;
    private Boolean callbackPublicKeyConfigured;
    private String callbackNotifyUrl;
    private String certificateProfile;
    private Integer notifySignWindowSec;
    private Integer refundWindowDays;
    private String riskControlTag;
    private String sceneScope;
    private String status;
    private String statusType;
    private BigDecimal dailyLimit;
    private Integer priority;
    private Integer routeWeight;
    private String changeType;
    private Integer sourceVersionNo;
    private String changeReason;
    private String createdAt;
}
