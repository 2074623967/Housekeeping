package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 渠道下单请求上下文。
 */
@Data
public class PaymentChannelSubmitRequestDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 订单号。 */
    private String orderNo;
    /** 支付场景。 */
    private String payScene;
    /** 客户名称。 */
    private String customerName;
    /** 支付金额。 */
    private BigDecimal amount;
    /** 支付方式。 */
    private String paymentMethod;
    /** 前端请求的渠道编码。 */
    private String requestedChannelCode;
    /** 路由后真实渠道编码。 */
    private String resolvedChannelCode;
    /** 发起终端。 */
    private String terminal;
    /** 客户端 IP。 */
    private String clientIp;
    /** 幂等键。 */
    private String idempotencyKey;
}
