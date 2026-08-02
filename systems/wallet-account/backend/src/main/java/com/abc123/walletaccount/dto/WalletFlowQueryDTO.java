package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletFlowQueryDTO {

    /** 钱包账户编号。 */
    private String walletAccountNo;
    /** 来源系统。 */
    private String sourceSystem;
    /** 来源业务单号。 */
    private String sourceBizNo;
    /** 页码，从 1 开始。 */
    private Integer pageNo = 1;
    /** 每页条数。 */
    private Integer pageSize = 20;
}
