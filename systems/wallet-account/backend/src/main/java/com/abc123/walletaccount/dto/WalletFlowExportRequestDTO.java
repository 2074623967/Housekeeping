package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletFlowExportRequestDTO {

    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 来源系统。 */
    private String sourceSystem;
    /** 来源业务单号。 */
    private String sourceBizNo;
    /** 导出操作人编号。 */
    private String operatorId;
    /** 导出操作人名称。 */
    private String operatorName;
}
