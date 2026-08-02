package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletAccountStatusChangeRequestDTO {

    private String targetStatus;
    private String operatorName;
    private String operationReason;
}
