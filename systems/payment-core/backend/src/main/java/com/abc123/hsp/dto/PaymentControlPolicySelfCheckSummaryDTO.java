package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付控制策略批量自检结果摘要。
 */
@Data
public class PaymentControlPolicySelfCheckSummaryDTO {

    /** 本次扫描策略数。 */
    private Integer processedCount;
    /** 自检通过数。 */
    private Integer passCount;
    /** 自检告警数。 */
    private Integer warnCount;
    /** 自检失败数。 */
    private Integer failCount;
}
