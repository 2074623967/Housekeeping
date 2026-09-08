package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 支付路由规则表达式校验与样例命中请求。
 */
@Data
public class PaymentRouteRuleValidationRequestDTO {

    /** 待校验表达式。 */
    private String matchExpression;
    /** 样例支付方式。 */
    private String paymentMethod;
    /** 样例请求渠道。 */
    private String requestedChannelCode;
    /** 样例支付场景。 */
    private String payScene;
    /** 样例终端。 */
    private String terminal;
    /** 样例金额。 */
    private BigDecimal amount;
    /** 样例客户名称。 */
    private String customerName;
}
