package com.abc123.riskcontrol.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付准入风控评估请求。
 */
@Data
public class RiskDecisionRequestDTO {

    /** 业务单号，支付场景下通常为支付单号。 */
    private String businessNo;
    /** 来源系统。 */
    private String sourceSystem;
    /** 风控场景编码。 */
    private String sceneCode;
    /** 支付场景。 */
    private String payScene;
    /** 支付方式。 */
    private String paymentMethod;
    /** 支付渠道编码。 */
    private String channelCode;
    /** 商户号。 */
    private String merchantNo;
    /** 发起终端。 */
    private String terminal;
    /** 客户端 IP。 */
    private String clientIp;
    /** 客户端设备号。 */
    private String clientDeviceId;
    /** 付款手机号。 */
    private String payerPhone;
    /** 交易金额。 */
    private BigDecimal amount;
}
