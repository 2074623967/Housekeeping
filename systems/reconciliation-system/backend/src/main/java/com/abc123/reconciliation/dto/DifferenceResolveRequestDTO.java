package com.abc123.reconciliation.dto;

import lombok.Data;

/**
 * 差异人工处置请求。
 */
@Data
public class DifferenceResolveRequestDTO {

    /** 差异编号。 */
    private String differenceNo;
    /** 处置结论。 */
    private String resolution;
    /** 处置备注。 */
    private String remark;
}

