package com.abc123.settlement.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果事件请求。
 */
@Data
public class ClearingGeneratedEventRequestDTO {

    /** 跨系统业务链路 ID；历史事件未传时按清分单号兼容生成。 */
    private String businessChainId;
    private String clearingNo;
    private String paymentOrderId;
    private String targetType;
    private String targetNo;
    private String targetName;
    private BigDecimal shouldSettleAmount;
    private BigDecimal deductAmount;
    private BigDecimal netSettleAmount;
}
