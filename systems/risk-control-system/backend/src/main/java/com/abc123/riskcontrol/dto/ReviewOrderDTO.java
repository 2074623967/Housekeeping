package com.abc123.riskcontrol.dto;

import lombok.Data;

/**
 * 人工复核单视图。
 */
@Data
public class ReviewOrderDTO {

    /** 复核单号。 */
    private String reviewNo;
    /** 业务单号。 */
    private String businessNo;
    /** 风险标签。 */
    private String riskTag;
    /** 待审事项。 */
    private String reviewItem;
    /** 当前状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 审核人。 */
    private String reviewer;
    /** 更新时间。 */
    private String updatedAt;
}

