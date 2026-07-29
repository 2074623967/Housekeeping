package com.abc123.settlement.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 创建结算单请求。
 */
@Data
public class CreateSettlementOrderRequestDTO {

    private String batchNo;
    private String targetType;
    private String targetNo;
    private String targetName;
    private BigDecimal shouldSettleAmount;
    private BigDecimal deductAmount;
}
