package com.abc123.clearing.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 清分结果实体。
 */
@Data
public class ClearingOrderEntity {

    /** 跨系统业务链路 ID。 */
    private String businessChainId;
    private String clearingNo;
    private String batchNo;
    private String paymentOrderId;
    /** 支付业务场景。 */
    private String payScene;
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
