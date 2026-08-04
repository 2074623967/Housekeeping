package com.abc123.reconciliation.dto;

import lombok.Data;

/**
 * 对账差异查询条件。
 */
@Data
public class DifferenceQueryDTO {

    /** 批次号。 */
    private String batchNo;
    /** 差异类型。 */
    private String differenceType;
    /** 差异状态。 */
    private String status;
    /** 页码。 */
    private int pageNo = 1;
    /** 页大小。 */
    private int pageSize = 20;
}

