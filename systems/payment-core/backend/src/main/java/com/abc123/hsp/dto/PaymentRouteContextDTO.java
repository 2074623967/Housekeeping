package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付路由决策上下文。
 */
@Data
public class PaymentRouteContextDTO {

    /** 支付方式。 */
    private String paymentMethod;
    /** 调用方请求的渠道编码。 */
    private String requestedChannelCode;
    /** 支付场景。 */
    private String payScene;
    /** 发起终端。 */
    private String terminal;
    /** 支付金额。 */
    private BigDecimal amount;
    /** 客户名称。 */
    private String customerName;
}
