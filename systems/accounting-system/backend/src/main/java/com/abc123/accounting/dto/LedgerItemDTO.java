package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 账户流水对象。
 */
@Data
public class LedgerItemDTO {

    private String ledgerNo;
    private String accountNo;
    private String bizType;
    private String bizNo;
    private String direction;
    private String amount;
    private String beforeBalance;
    private String afterBalance;
    private String ledgerStatus;
    private String statusType;
    private String operatorName;
    private String createdAt;
}
