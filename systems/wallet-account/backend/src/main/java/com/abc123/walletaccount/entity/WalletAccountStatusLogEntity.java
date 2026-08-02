package com.abc123.walletaccount.entity;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletAccountStatusLogEntity {

    /** 数据库主键。 */
    private Long id;
    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 变更前状态。 */
    private String beforeStatus;
    /** 变更后状态。 */
    private String afterStatus;
    /** 变更原因编码。 */
    private String reasonCode;
    /** 变更原因说明。 */
    private String reasonDesc;
    /** 操作人编号。 */
    private String operatorId;
    /** 操作人名称。 */
    private String operatorName;
    /** 记录创建时间。 */
    private LocalDateTime createdAt;
}
