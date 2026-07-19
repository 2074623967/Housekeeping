package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付收款记录查询条件。
 */
@Data
public class PaymentRecordQueryDTO {

    /** 记录维度：ALL、WECHAT、BANK_CARD。 */
    private String recordType = "ALL";
    /** 用户 ID。 */
    private String userId;
    /** 业务订单号/商户订单号。 */
    private String businessOrderNo;
    /** 支付类型。 */
    private String paymentType;
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
