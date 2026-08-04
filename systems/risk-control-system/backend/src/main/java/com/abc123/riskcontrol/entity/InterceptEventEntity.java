package com.abc123.riskcontrol.entity;

import lombok.Data;

/**
 * 风险拦截事件实体。
 */
@Data
public class InterceptEventEntity {

    /** 主键。 */
    private Long id;
    /** 事件编号。 */
    private String eventNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 命中策略。 */
    private String hitPolicy;
    /** 风险等级。 */
    private String riskLevel;
    /** 处置结果。 */
    private String decisionResult;
    /** 来源系统。 */
    private String sourceSystem;
    /** 发生时间。 */
    private String happenedAt;
}

