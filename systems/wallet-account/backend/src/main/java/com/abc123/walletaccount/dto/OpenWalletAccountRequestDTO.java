package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class OpenWalletAccountRequestDTO {

    private String walletOwnerId;
    private String ownerType;
    private String ownerName;
    private String bizLineCode;
    private String tenantCode;
    private String extRefNo;
    private String accountType;
    private String accountScene;
    private String currencyCode = "CNY";
    private Boolean allowCredit = Boolean.FALSE;
    private String riskLevel = "LOW";
    private String operatorName = "system";
}
