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
    /** 客户端 IP。 */
    private String clientIp;
    /** 请求状态，全部时不筛选。 */
    private String requestStatus = "全部";
    /** 排序字段。 */
    private String sortField = "createdAt";
    /** 排序方向。 */
    private String sortOrder = "desc";
    /** 页码，从 1 开始。 */
    private int pageNo = 1;
    /** 每页条数，最大 100。 */
    private int pageSize = 20;

    public String getRequestNoLikePattern() {
        return LikePattern.escape(requestNo);
    }

    public String getPaymentOrderIdLikePattern() {
        return LikePattern.escape(paymentOrderId);
    }

    public String getOrderNoLikePattern() {
        return LikePattern.escape(orderNo);
    }

    public String getChannelCodeLikePattern() {
        return LikePattern.escape(channelCode);
    }

    public String getClientIpLikePattern() {
        return LikePattern.escape(clientIp);
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
