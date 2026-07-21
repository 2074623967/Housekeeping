package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 退款详情聚合对象。
 */
@Data
public class RefundDetailDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 原订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 退款金额。 */
    private String refundAmount;
    /** 退款方式。 */
    private String refundMethod;
    /** 退款原因。 */
    private String refundReason;
    /** 退款状态。 */
    private String status;
    /** 退款状态样式。 */
    private String statusType;
    /** 申请时间。 */
    private String appliedAt;
    /** 成功时间。 */
    private String successAt;
    /** 原支付金额。 */
    private String paidAmount;
    /** 原支付方式。 */
    private String paymentMethod;
    /** 原支付状态。 */
    private String paymentStatus;
    /** 操作日志。 */
    private List<RefundOperationLogItemDTO> operationLogs;
}
