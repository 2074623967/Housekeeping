package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款操作日志列表项。
 */
@Data
public class RefundOperationLogItemDTO {

    /** 日志号。 */
    private String logNo;
    /** 动作编码。 */
    private String actionCode;
    /** 动作名称。 */
    private String actionName;
    /** 原状态。 */
    private String fromStatus;
    /** 目标状态。 */
    private String toStatus;
    /** 操作人。 */
    private String operatorName;
    /** 操作备注。 */
    private String operationRemark;
    /** 操作时间。 */
    private String createdAt;
}
