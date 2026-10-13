package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付收款记录查询条件。
 */
@Data
public class PaymentRecordQueryDTO {

    /** 记录维度：ALL、WECHAT、BANK_CARD。 */
    private String recordType = "ALL";
    /** 历史查询字段名，实际按客户姓名筛选。 */
    private String userId;
    /** 业务订单号/商户订单号。 */
    private String businessOrderNo;
    /** 支付类型。 */
    private String paymentType;
    /** 支付状态。 */
    private String paymentStatus = "全部";
    /** 支付渠道。 */
    private String paymentChannel = "";
    /** 排序字段。 */
    private String sortField = "createdAt";
    /** 排序方向。 */
    private String sortOrder = "desc";
    /** 页码，从 1 开始。 */
    private int pageNo = 1;
    /** 每页条数，最大 100。 */
    private int pageSize = 20;

    public String getUserIdLikePattern() {
        return escapeLikePattern(userId);
    }

    public String getBusinessOrderNoLikePattern() {
        return escapeLikePattern(businessOrderNo);
    }

    public String getPaymentChannelLikePattern() {
        return escapeLikePattern(paymentChannel);
    }

    private String escapeLikePattern(String value) {
        return value == null ? null : value.replace("!", "!!").replace("%", "!%").replace("_", "!_");
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
