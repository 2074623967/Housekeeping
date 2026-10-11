package com.abc123.refund.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Locale;
import lombok.Data;

/**
 * 退款列表项。
 */
@Data
public class RefundListItemDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 原支付业务场景。 */
    private String payScene;

    /** 统一业务链路编号，由原支付单号稳定派生。 */
    public String getBusinessChainId() {
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }

    /** 业务订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 原支付金额。 */
    private BigDecimal paidAmount;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式。 */
    private String refundMethod;
    /** 退款原因。 */
    private String refundReason;
    /** 退款状态。 */
    private String status;
    /** 退款状态展示样式。 */
    private String statusType;
    /** 渠道退款流水号。 */
    private String channelRefundId;
    /** 原支付渠道。 */
    private String channelCode;
    /** 渠道失败码。 */
    private String failureCode;
    /** 渠道提交时间。 */
    private LocalDateTime channelSubmittedAt;
    /** 申请时间。 */
    private LocalDateTime appliedAt;
    /** 成功时间。 */
    private LocalDateTime successAt;
    /** 退款终态时间。 */
    private LocalDateTime terminalAt;

    public String getStatusType() {
        if (status == null) {
            return null;
        }
        String normalizedStatus = status.trim().toUpperCase(Locale.ROOT);
        if ("REVIEWING".equals(normalizedStatus) || "PROCESSING".equals(normalizedStatus)) {
            return "warn";
        }
        if ("APPROVED".equals(normalizedStatus) || "SUCCESS".equals(normalizedStatus)) {
            return "success";
        }
        if ("FAIL".equals(normalizedStatus)) {
            return "danger";
        }
        return null;
    }
}
