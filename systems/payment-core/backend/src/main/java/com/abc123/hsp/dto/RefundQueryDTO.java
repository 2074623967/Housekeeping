package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款单列表查询条件。
 */
@Data
public class RefundQueryDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 退款状态。 */
    private String refundStatus;
    /** 退款方式。 */
    private String refundMethod;
    /** 页码。 */
    private int pageNo = 1;
    /** 每页条数。 */
    private int pageSize = 20;

    public int getOffset() {
        return (Math.max(pageNo, 1) - 1) * Math.min(Math.max(pageSize, 1), 100);
    }

    public int getLimit() {
        return Math.min(Math.max(pageSize, 1), 100);
    }
}
