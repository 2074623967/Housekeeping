package com.abc123.deposit.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 保证金规则绑定预览。
 */
@Data
public class DepositRuleBindingPreviewDTO {

    /** 规则编码。 */
    private String ruleCode;
    /** 规则名称。 */
    private String ruleName;
    /** 业务线编码。 */
    private String businessLine;
    /** 品类编码。 */
    private String categoryCode;
    /** 来源账户号。 */
    private String sourceAccountNo;
    /** 来源主体编号。 */
    private String sourceOwnerId;
    /** 规则应缴金额。 */
    private BigDecimal ruleAmount;
    /** 当前余额。 */
    private BigDecimal currentBalance;
    /** 冻结金额。 */
    private BigDecimal frozenAmount;
    /** 绑定状态。 */
    private String bindingStatus;
}
