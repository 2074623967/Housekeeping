package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 路由规则复核申请展示项。
 */
@Data
public class PaymentRouteRuleReviewDTO {

    private String requestNo;
    private String ruleCode;
    private Integer baseVersionNo;
    private Integer sourceVersionNo;
    private String operationType;
    private String ruleName;
    private String matchScene;
    private String matchExpression;
    private String targetChannelCode;
    private String fallbackChannelCode;
    private Integer rolloutPercentage;
    private String rolloutSalt;
    private String status;
    private String statusType;
    private Integer priority;
    private String requester;
    private String requestReason;
    private String reviewStatus;
    private String reviewStatusType;
    private String reviewer;
    private String reviewComment;
    /** 批准复验是否检测到同优先级潜在冲突；未执行批准复验时为空。 */
    private Boolean priorityCollisionDetected;
    /** 检测到潜在冲突时是否完成显式风险确认；未执行批准复验时为空。 */
    private Boolean priorityCollisionAcknowledged;
    /** 批准复验检测到的对照规则编码 JSON 数组，按编码稳定排序。 */
    private String priorityCollisionRuleCodesJson;
    /** 待办候选基于当前运行态计算的同优先级冲突规则编码预览。 */
    private List<String> previewPriorityCollisionRuleCodes;
    /** 待办候选基于当前运行态计算的阻断级冲突预览。 */
    private List<PaymentRouteRuleConflictDTO> previewBlockingConflicts;
    private String createdAt;
    private String reviewedAt;
    /** 身份可信度固定为自声明，避免被误读成已认证签字。 */
    private String identityTrustLevel;
}
