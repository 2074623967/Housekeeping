package com.abc123.accounting.dto;

import lombok.Data;

/**
 * 冻结单对象。
 */
@Data
public class FreezeItemDTO {

    private String freezeNo;
    private String accountNo;
    private String bizNo;
    private String freezeType;
    private String freezeReason;
    private String freezeAmount;
    private String freezeStatus;
    private String statusType;
    private String operatorName;
    private String createdAt;
    private String unfrozenAt;
}
