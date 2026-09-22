package com.abc123.reconciliation.dto;

import lombok.Data;

/**
 * 对账批次分页查询条件。
 */
@Data
public class BatchQueryDTO {

    /** 页码。 */
    private int pageNo = 1;
    /** 页大小。 */
    private int pageSize = 20;
}
