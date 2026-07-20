package com.abc123.accounting.entity;

import lombok.Data;

/**
 * 账户主实体。
 */
@Data
public class AccountEntity {

    /** 账户号。 */
    private String accountNo;
    /** 主体 ID。 */
    private String subjectId;
    /** 主体名称。 */
    private String subjectName;
    /** 账户类型。 */
    private String accountType;
    /** 账户状态。 */
    private String accountStatus;
    /** 币种。 */
    private String currency;
    /** 创建时间。 */
    private String createdAt;
    /** 最近变更时间。 */
    private String lastChangeAt;
}
