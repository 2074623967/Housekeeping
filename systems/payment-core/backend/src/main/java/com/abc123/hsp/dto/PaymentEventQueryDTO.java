package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付事件出站查询条件。
 */
@Data
public class PaymentEventQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 事件类型。 */
    private String eventType = "全部";
    /** 发布状态。 */
    private String publishStatus = "全部";
    /** 下游系统。 */
    private String downstreamSystem = "全部";
    /** 事件主题关键字。 */
    private String eventTopic;
    /** 排序字段。 */
    private String sortField = "createdAt";
    /** 排序方向。 */
    private String sortOrder = "desc";
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
