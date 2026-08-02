package com.abc123.walletaccount.dto;

import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletAccountStatusLogDTO {

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
    /** 变更时间。 */
    private LocalDateTime createdAt;
}
