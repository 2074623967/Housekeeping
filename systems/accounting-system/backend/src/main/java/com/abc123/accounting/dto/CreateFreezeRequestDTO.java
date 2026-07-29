package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建冻结单请求。
 */
@Data
public class CreateFreezeRequestDTO {

    private String accountNo;
    private String bizNo;
    private String freezeType;
    private String freezeReason;
    private String operatorName;
    private BigDecimal freezeAmount;
}
