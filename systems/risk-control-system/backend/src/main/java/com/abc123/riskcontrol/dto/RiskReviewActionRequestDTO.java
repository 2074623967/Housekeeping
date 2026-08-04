package com.abc123.riskcontrol.dto;

import lombok.Data;

/**
 * 风险复核动作请求。
 */
@Data
public class RiskReviewActionRequestDTO {

    /** 复核单号。 */
    private String reviewNo;
    /** 审核动作。 */
    private String action;
    /** 审核备注。 */
    private String remark;
}

