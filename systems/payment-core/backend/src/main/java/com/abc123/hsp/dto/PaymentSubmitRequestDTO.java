package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付提交请求，除支付方式外还记录终端、IP 和幂等键，便于真实排障。
 */
@Data
public class PaymentSubmitRequestDTO {

    /** 预付单号。 */
    private String prepayOrderNo;
    /** 支付方式。 */
    private String paymentMethod;
    /** 渠道编码。 */
    private String channelCode;
    /** 来源应用标识。 */
    private String sourceAppId;
    /** 商户号。 */
    private String merchantNo;
    /** 接口访问令牌。 */
    private String accessToken;
    /** 发起终端。 */
    private String terminal;
    /** 客户端 IP；HTTP 入口会以可信连接上下文覆盖请求体值。 */
    private String clientIp;
    /** 客户端设备号。 */
    private String clientDeviceId;
    /** 付款手机号。 */
    private String payerPhone;
    /** 幂等键。 */
    private String idempotencyKey;
}
