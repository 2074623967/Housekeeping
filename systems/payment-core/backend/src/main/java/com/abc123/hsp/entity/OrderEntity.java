package com.abc123.hsp.entity;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 订单实体，对应表：t_order。
 */
@Data
public class OrderEntity {

    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 服务品类。 */
    private String serviceType;
    /** 服务者名称。 */
    private String workerName;
    /** 订单应收金额。 */
    private BigDecimal orderAmount;
    /** 订单已支付金额。 */
    private BigDecimal paidAmount;
    /** 订单状态。 */
    private String orderStatus;
    /** 订单状态样式类型。 */
    private String orderStatusType;
    /** 履约状态。 */
    private String fulfillmentStatus;
    /** 履约状态样式类型。 */
    private String fulfillmentStatusType;
}
