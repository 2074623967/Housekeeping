package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 账户列表项。
 */
@Data
public class AccountListItemDTO {

    private String accountNo;
    private String subjectId;
    private String subjectName;
    private String accountType;
    private String accountStatus;
    private String statusType;
    private String currency;
    private String availableAmount;
    private String frozenAmount;
    private String inTransitAmount;
    private String lastChangeAt;
}
