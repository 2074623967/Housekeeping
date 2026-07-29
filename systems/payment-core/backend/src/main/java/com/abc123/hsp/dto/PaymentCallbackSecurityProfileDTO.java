package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付回调验签安全配置。
 */
@Data
public class PaymentCallbackSecurityProfileDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 回调签名算法。 */
    private String callbackSignAlgorithm;
    /** 回调验签密钥。 */
    private String callbackSecret;
    /** 回调验签公钥。 */
    private String callbackPublicKey;
    /** 回调通知地址。 */
    private String callbackNotifyUrl;
    /** 回调验签时间窗。 */
    private Integer notifySignWindowSec;
}
