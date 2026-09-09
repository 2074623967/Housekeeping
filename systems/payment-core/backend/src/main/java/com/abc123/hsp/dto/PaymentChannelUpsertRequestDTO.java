package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 支付渠道新增或编辑候选。 */
@Data
public class PaymentChannelUpsertRequestDTO {

    private String channelCode;
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
    private Boolean enabled;
    private BigDecimal dailyLimit;
    private Integer priority;
    private Integer routeWeight;
    private String changeReason;
}
