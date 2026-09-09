package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/** 支付渠道不可变版本实体。 */
@Data
public class PaymentChannelVersionEntity {

    private String channelCode;
    private Integer versionNo;
    private String channelName;
    private String paymentMethod;
    private String merchantNo;
    private String merchantAppId;
    private String callbackSecret;
    private String callbackSignAlgorithm;
    private String callbackPublicKey;
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
}
