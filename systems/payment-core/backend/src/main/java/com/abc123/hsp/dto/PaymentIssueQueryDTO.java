package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付交易异常中心查询条件。
 */
@Data
public class PaymentIssueQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 异常类型。 */
    private String issueType = "全部";
    /** 严重等级。 */
    private String severity = "全部";
    /** 渠道编码。 */
    private String channelCode;
    /** 支付方式。 */
    private String paymentMethod = "全部";
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
