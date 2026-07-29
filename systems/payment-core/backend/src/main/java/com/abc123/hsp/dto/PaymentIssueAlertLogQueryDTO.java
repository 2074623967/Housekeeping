package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付异常告警明细查询条件。
 */
@Data
public class PaymentIssueAlertLogQueryDTO {

    /** 告警编号。 */
    private String alertNo;
    /** 异常编号。 */
    private String issueNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 告警通道。 */
    private String alertChannel = "全部";
    /** 告警状态。 */
    private String alertStatus = "全部";
    /** 回执状态。 */
    private String ackStatus = "全部";
    /** 供应商投递状态。 */
    private String providerDeliveryStatus = "全部";
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
