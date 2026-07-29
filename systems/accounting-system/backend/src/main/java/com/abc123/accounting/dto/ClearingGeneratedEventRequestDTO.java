package com.abc123.accounting.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果事件请求。
 */
@Data
public class ClearingGeneratedEventRequestDTO {

    private String accountNo;
    private String clearingOrderNo;
    private String bizNo;
    private BigDecimal amount;
    private String summary;
}
