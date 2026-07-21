package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 退款操作日志实体，对应表：t_refund_operation_log。
 */
@Data
public class RefundOperationLogEntity {

    /** 日志号。 */
    private String logNo;
    /** 退款单号。 */
    private String refundOrderId;
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
}
