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
    /** 订单号。 */
    private String orderNo;
    /** 渠道编码。 */
    private String channelCode;
    /** 发起终端。 */
    private String terminal;
    /** 请求状态，全部时不筛选。 */
    private String requestStatus = "全部";
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
