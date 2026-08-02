package com.abc123.walletaccount.entity;

import lombok.Data;

@Data
public class WalletOwnerEntity {

    private Long id;
    private String walletOwnerId;
    private String ownerType;
    private String ownerName;
    private String ownerStatus;
    private String bizLineCode;
    private String tenantCode;
    private String extRefNo;
}
