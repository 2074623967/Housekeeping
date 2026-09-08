package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由规则表达式校验与样例命中结果。
 */
@Data
public class PaymentRouteRuleValidationDTO {

    /** 表达式是否合法。 */
    private Boolean valid;
    /** 样例上下文是否命中。 */
    private Boolean matched;
    /** 校验结果说明。 */
    private String message;
}
