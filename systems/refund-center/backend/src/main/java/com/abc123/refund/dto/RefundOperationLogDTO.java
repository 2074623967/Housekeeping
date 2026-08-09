package com.abc123.refund.dto;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 退款操作日志。
 */
@Data
public class RefundOperationLogDTO {

    /** 操作流水号。 */
    private String logNo;
    /** 退款单号。 */
    private String refundOrderId;
    /** 动作编码。 */
    private String actionCode;
    /** 动作名称。 */
    private String actionName;
    /** 原状态。 */
    private String fromStatus;
    /** 新状态。 */
    private String toStatus;
    /** 操作人。 */
    private String operatorName;
    /** 操作备注。 */
    private String operationRemark;
    /** 操作时间。 */
    private LocalDateTime operatedAt;
}

