package com.abc123.clearing.dto;

import lombok.Data;

/**
 * 清分结果列表对象。
 */
@Data
public class ClearingOrderDTO {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    private String clearingNo;
    private String batchNo;
    private String paymentOrderId;
    /** 支付业务场景。 */
    private String payScene;
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
