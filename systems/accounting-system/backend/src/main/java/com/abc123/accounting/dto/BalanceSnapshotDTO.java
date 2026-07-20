package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 余额详情对象。
 */
@Data
public class BalanceSnapshotDTO {

    private String accountNo;
    private String subjectName;
    private String accountType;
    private String accountStatus;
    private String availableAmount;
    private String frozenAmount;
    private String inTransitAmount;
    private String updatedAt;
}
