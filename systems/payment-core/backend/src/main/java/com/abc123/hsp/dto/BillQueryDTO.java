package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 交易账单查询条件。
 */
@Data
public class BillQueryDTO {

    /** 账单号。 */
    private String billNo;
    /** 订单号。 */
    private String orderNo;
    /** 账单状态。 */
    private String billStatus = "全部";
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
