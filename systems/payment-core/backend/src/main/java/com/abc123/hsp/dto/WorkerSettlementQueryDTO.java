package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 服务者结算单列表查询条件。
 */
@Data
public class WorkerSettlementQueryDTO {

    /** 结算单号。 */
    private String settlementOrderId;
    /** 服务者关键字。 */
    private String workerKeyword;
    /** 结算状态。 */
    private String settlementStatus;
    /** 出款状态。 */
    private String payoutStatus;
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
