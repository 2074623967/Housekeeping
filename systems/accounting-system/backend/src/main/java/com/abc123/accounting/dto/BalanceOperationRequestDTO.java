package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 余额变更请求。
 */
@Data
public class BalanceOperationRequestDTO {

    private String accountNo;
    private String bizNo;
    private String bizType;
    private String idempotencyKey;
    private String operatorName;
    private BigDecimal amount;
    private String remark;
}
