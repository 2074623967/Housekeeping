package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道维度支付监控指标。
 */
@Data
public class PaymentChannelMetricDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 支付方式。 */
    private String paymentMethod;
    /** 支付单总量。 */
    private Integer totalCount;
    /** 成功支付单量。 */
    private Integer successCount;
    /** 成功率。 */
    private String successRate;
    /** 成功金额。 */
    private String successAmount;
    /** 待处理笔数。 */
    private Integer pendingCount;
    /** 风险等级。 */
    private String riskLevel;
    /** 风险等级样式。 */
    private String riskLevelType;
    /** 风险说明。 */
    private String riskHint;
}
