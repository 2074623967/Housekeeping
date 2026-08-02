package com.abc123.walletaccount.entity;

import lombok.Data;

@Data
public class WalletOwnerEntity {

    /** 数据库主键。 */
    private Long id;
    /** 钱包主体编号。 */
    private String walletOwnerId;
    /** 主体类型。 */
    private String ownerType;
    /** 主体名称。 */
    private String ownerName;
    /** 主体状态。 */
    private String ownerStatus;
    /** 业务线编码。 */
    private String bizLineCode;
    /** 租户编码。 */
    private String tenantCode;
    /** 外部主体编号。 */
    private String extRefNo;
}
