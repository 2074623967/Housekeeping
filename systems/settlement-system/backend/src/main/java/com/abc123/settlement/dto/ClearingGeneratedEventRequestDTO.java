package com.abc123.settlement.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果事件请求。
 */
@Data
public class ClearingGeneratedEventRequestDTO {

    private String clearingNo;
    private String paymentOrderId;
    private String targetType;
    private String targetNo;
    private String targetName;
    private BigDecimal shouldSettleAmount;
    private BigDecimal deductAmount;
    private BigDecimal netSettleAmount;
}
