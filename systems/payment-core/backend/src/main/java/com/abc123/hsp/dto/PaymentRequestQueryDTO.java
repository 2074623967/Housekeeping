package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付请求查询条件。
 */
@Data
public class PaymentRequestQueryDTO {

    /** 请求编号。 */
    private String requestNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 请求状态，全部时不筛选。 */
    private String requestStatus = "全部";
}
