package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付单管理查询条件。
 */
@Data
public class PaymentListQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 支付方式。 */
    private String paymentMethod = "全部";
    /** 支付状态。 */
    private String status = "全部";
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
