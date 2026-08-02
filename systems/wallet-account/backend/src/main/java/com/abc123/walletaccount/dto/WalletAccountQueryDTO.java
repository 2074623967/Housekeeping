package com.abc123.walletaccount.dto;

import lombok.Data;

@Data
public class WalletAccountQueryDTO {

    private String keyword;
    private String ownerType;
    private String accountStatus;
    private Integer pageNo = 1;
    private Integer pageSize = 20;
}
