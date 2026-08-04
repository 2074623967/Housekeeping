package com.abc123.reconciliation.dto;

import lombok.Data;

/**
 * 对账工作台摘要。
 */
@Data
public class ReconciliationOverviewDTO {

    /** 批次总数。 */
    private long batchCount;
    /** 运行中批次数。 */
    private long runningBatchCount;
    /** 未结案差异数。 */
    private long openDifferenceCount;
    /** 已匹配记录数。 */
    private long matchedCount;
}

