package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 收银台会话查询条件。
 */
@Data
public class CashierSessionQueryDTO {

    /** 会话号/预付单号。 */
    private String sessionNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 终端场景。 */
    private String terminal = "全部";
    /** 会话状态。 */
    private String sessionStatus = "全部";
    /** 排序字段。 */
    private String sortField = "createdAt";
    /** 排序方向。 */
    private String sortOrder = "desc";
    /** 页码，从 1 开始。 */
    private int pageNo = 1;
    /** 每页条数，最大 100。 */
    private int pageSize = 20;

    public String getSessionNoLikePattern() {
        return LikePattern.escape(sessionNo);
    }

    public String getPaymentOrderIdLikePattern() {
        return LikePattern.escape(paymentOrderId);
    }

    public String getOrderNoLikePattern() {
        return LikePattern.escape(orderNo);
    }

    public String getCustomerNameLikePattern() {
        return LikePattern.escape(customerName);
    }

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
