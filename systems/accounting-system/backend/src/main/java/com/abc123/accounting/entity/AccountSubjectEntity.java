package com.abc123.accounting.entity;

import lombok.Data;

/**
 * 账户主体实体。
 */
@Data
public class AccountSubjectEntity {

    /** 主体 ID。 */
    private String subjectId;
    /** 主体类型。 */
    private String subjectType;
    /** 主体名称。 */
    private String subjectName;
    /** 归属人名称。 */
    private String ownerName;
    /** 主体状态。 */
    private String status;
    /** 创建时间。 */
    private String createdAt;
}
