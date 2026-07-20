package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分结果列表对象。
 */
@Data
public class ClearingOrderDTO {

    private String clearingNo;
    private String batchNo;
    private String paymentOrderId;
    private String orderNo;
    private String orderAmount;
    private String merchantAmount;
    private String workerAmount;
    private String platformAmount;
    private String channelFeeAmount;
    private String subsidyAmount;
    private String clearingStatus;
    private String clearingStatusType;
    private String ruleNo;
    private String createdAt;
}
