package com.abc123.riskcontrol.entity;

import lombok.Data;

/**
 * 黑名单实体。
 */
@Data
public class BlocklistEntity {

    /** 主键。 */
    private Long id;
    /** 名单编码。 */
    private String blockCode;
    /** 主体值。 */
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

