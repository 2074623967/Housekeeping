package com.abc123.refund.dto;

import lombok.Data;

/**
 * 退款列表查询条件。
 */
@Data
public class RefundQueryDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 退款状态。 */
    private String status;
    /** 退款方式。 */
    private String refundMethod;
    /** 页码。 */
    private int pageNo = 1;
    /** 页大小。 */
    private int pageSize = 20;
}

