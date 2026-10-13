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
    /** 回执回查队列状态。 */
    private String receiptQueryState = "全部";
    /** 回执回查首次等待秒数，由服务端按配置写入。 */
    private int receiptQueryMinAgeSeconds;
    /** 自动回查最大次数，由服务端按配置写入。 */
    private int receiptQueryMaxAttempts;
    /** 回执回查占用超时秒数，由服务端按配置写入。 */
    private int receiptQueryClaimTimeoutSeconds;
    /** 页码。 */
    private int pageNo = 1;
    /** 每页条数。 */
    private int pageSize = 20;

    public int getPageNo() {
        return PageQueryBounds.normalizePageNo(pageNo);
    }

    public int getOffset() {
        return PageQueryBounds.offset(pageNo, pageSize);
    }

    public int getLimit() {
        return PageQueryBounds.normalizePageSize(pageSize);
    }

    public String getAlertNoLikePattern() {
        return LikePattern.escape(alertNo);
    }

    public String getIssueNoLikePattern() {
        return LikePattern.escape(issueNo);
    }

    public String getPaymentOrderIdLikePattern() {
        return LikePattern.escape(paymentOrderId);
    }
}
