package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付路由执行结果查询条件。
 */
@Data
public class PaymentRouteExecutionQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 路由规则关键字。 */
    private String routeRule;
    /** 渠道编码。 */
    private String channelCode;
    /** 路由结果。 */
    private String routeResult = "全部";
    /** 页码，从 1 开始。 */
    private int pageNo = 1;
    /** 每页条数，最大 100。 */
    private int pageSize = 20;

    public int getOffset() {
        return (Math.max(pageNo, 1) - 1) * Math.min(Math.max(pageSize, 1), 100);
    }

    public int getLimit() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
