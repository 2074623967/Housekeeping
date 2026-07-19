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
    /** 发起终端。 */
    private String terminal;
    /** 客户端 IP。 */
    private String clientIp;
    /** 幂等键。 */
    private String idempotencyKey;
}
