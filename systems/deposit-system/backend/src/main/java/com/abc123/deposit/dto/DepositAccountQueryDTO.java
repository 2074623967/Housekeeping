package com.abc123.deposit.dto;

import lombok.Data;

/**
 * 保证金账户查询条件。
 */
@Data
public class DepositAccountQueryDTO {

    /** 主体类型。 */
    private String ownerType;
    /** 账户状态。 */
    private String status;
    /** 关键词，支持账户号或主体编号。 */
    private String keyword;
}
