package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class OpenWalletAccountRequestDTO {

    /** 请求幂等号。 */
    private String requestNo;
    /** 钱包主体编号。 */
    private String walletOwnerId;
    /** 主体类型。 */
    private String ownerType;
    /** 主体名称。 */
    private String ownerName;
    /** 业务线编码。 */
    private String bizLineCode;
    /** 租户编码。 */
    private String tenantCode;
    /** 外部主体编号。 */
    private String extRefNo;
    /** 账户类型。 */
    private String accountType;
    /** 账户场景。 */
    private String accountScene;
    /** 币种编码，默认 CNY。 */
    private String currencyCode = "CNY";
    /** 是否允许透支，默认不允许。 */
    private Boolean allowCredit = Boolean.FALSE;
    /** 风险等级，默认 LOW。 */
    private String riskLevel = "LOW";
    /** 操作人编号。 */
    private String operatorId = "system";
    /** 操作人名称。 */
    private String operatorName = "system";
}
