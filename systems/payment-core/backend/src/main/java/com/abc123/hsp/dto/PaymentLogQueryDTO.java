package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付处理日志查询条件。
 */
@Data
public class PaymentLogQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 处理阶段。 */
    private String processStage = "全部";
    /** 日志级别。 */
    private String logLevel = "全部";
    /** 日志来源。 */
    private String source;
    /** 日志关键字。 */
    private String keyword;
    /** 排序字段。 */
    private String sortField = "createdAt";
    /** 排序方向。 */
    private String sortOrder = "desc";
    /** 页码，从 1 开始。 */
    private int pageNo = 1;
    /** 每页条数，最大 100。 */
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
}
