package com.abc123.refund.dto;

import java.util.List;
import lombok.Data;

/**
 * 退款详情。
 */
@Data
public class RefundDetailDTO extends RefundListItemDTO {

    /** 操作日志。 */
    private List<RefundOperationLogDTO> operationLogs;
}

