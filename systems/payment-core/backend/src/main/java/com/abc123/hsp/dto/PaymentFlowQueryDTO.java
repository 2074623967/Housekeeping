package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付流水查询条件。
 */
@Data
public class PaymentFlowQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 流水类型。 */
    private String flowType = "全部";
}
