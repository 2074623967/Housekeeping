package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 路由规则复核动作参数。
 */
@Data
public class PaymentRouteRuleReviewActionRequestDTO {

    /** 复核人声明；当前未接入可信身份源。 */
    private String reviewer;
    /** 复核意见。 */
    private String reviewComment;
    /** 是否已确认同优先级不同表达式可能同时命中的路由风险。 */
    private Boolean priorityCollisionAcknowledged;
    /** 复核人确认时看到的同优先级冲突规则编码集合。 */
    private List<String> acknowledgedPriorityCollisionRuleCodes;
}
