package com.abc123.clearing.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果实体。
 */
@Data
public class ClearingOrderEntity {

    private String clearingNo;
    private String batchNo;
    private String paymentOrderId;
    private String orderNo;
    private BigDecimal orderAmount;
    private BigDecimal merchantAmount;
    private BigDecimal workerAmount;
    private BigDecimal platformAmount;
    private BigDecimal channelFeeAmount;
    private BigDecimal subsidyAmount;
    private String clearingStatus;
    private String ruleNo;
    private String createdAt;
}
