package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 订单中心查询条件。
 */
@Data
public class OrderQueryDTO {

    /** 订单号。 */
    private String orderNo;
    /** 服务品类。 */
    private String serviceType = "全部";
    /** 订单状态。 */
    private String orderStatus = "全部";
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
