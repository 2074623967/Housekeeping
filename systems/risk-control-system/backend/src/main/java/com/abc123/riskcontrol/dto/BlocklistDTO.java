package com.abc123.riskcontrol.dto;

import lombok.Data;

/**
 * 黑名单视图。
 */
@Data
public class BlocklistDTO {

    /** 名单编码。 */
    private String blockCode;
    /** 命中主体。 */
    private String subjectValue;
    /** 主体类型。 */
    private String subjectType;
    /** 命中原因。 */
    private String reason;
    /** 处置动作。 */
    private String actionType;
    /** 状态。 */
    private String status;
    /** 状态样式。 */
    private String statusType;
    /** 更新时间。 */
    private String updatedAt;
}

