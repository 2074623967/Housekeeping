package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletAccountQueryDTO {

    /** 账户编号或主体名称关键字。 */
    private String keyword;
    /** 主体类型。 */
    private String ownerType;
    /** 账户状态。 */
    private String accountStatus;
    /** 页码，从 1 开始。 */
    private Integer pageNo = 1;
    /** 每页条数。 */
    private Integer pageSize = 20;
}
