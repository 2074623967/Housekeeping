package com.abc123.wallet.dto;

import java.math.BigDecimal;
import lombok.Data;

/** 钱包转账请求。 */
@Data
public class WalletTransferRequestDTO {
    /** 转出账户号。 */
    private String sourceAccountNo;
    /** 转入账户号。 */
    private String targetAccountNo;
    /** 业务单号。 */
    private String bizNo;
    /** 转账金额。 */
    private BigDecimal amount;
    /** 操作人。 */
    private String operatorName;
}
