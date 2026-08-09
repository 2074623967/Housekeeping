package com.abc123.walletaccount.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

@Data
public class WalletFlowDTO {

    /** 钱包流水编号。 */
    private String flowNo;
    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 流水类型。 */
    private String flowType;
    /** 来源系统。 */
    private String sourceSystem;
    /** 来源业务单号。 */
    private String sourceBizNo;
    /** 余额变动金额。 */
    private BigDecimal changeAmount;
    /** 变动前可用余额。 */
    private BigDecimal beforeAvailableBalance;
    /** 变动后可用余额。 */
    private BigDecimal afterAvailableBalance;
    /** 操作人名称。 */
    private String operatorName;
    /** 操作原因。 */
    private String operationReason;
    /** 流水创建时间。 */
    private LocalDateTime createdAt;
}
