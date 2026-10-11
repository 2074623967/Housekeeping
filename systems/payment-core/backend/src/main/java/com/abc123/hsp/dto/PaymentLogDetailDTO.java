package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付处理日志唯一详情。
 */
@Data
public class PaymentLogDetailDTO {

    /** 日志编号。 */
    private String logNo;
    /** 支付单号。 */
    private String paymentOrderId;

    /** 跨系统业务链路 ID，按支付单稳定派生为 CHAIN-{paymentOrderId}。 */
    public String getBusinessChainId() {
        return paymentOrderId == null || paymentOrderId.trim().isEmpty()
                ? null : "CHAIN-" + paymentOrderId.trim();
    }
    /** 业务订单号。 */
    private String orderNo;
    /** 真实存在于标准订单表中的订单号。 */
    private String standardOrderNo;
    /** 支付请求号。 */
    private String requestNo;
    /** 来源事实类型。 */
    private String sourceType;
    /** 来源事实编号。 */
    private String sourceNo;
    /** 处理阶段。 */
    private String processStage;
    /** 日志级别。 */
    private String logLevel;
    /** 日志级别样式。 */
    private String logLevelType;
    /** 所属支付单成功事实异常类型；正常支付单为空。 */
    private String successFactIssue;
    /** 日志消息。 */
    private String message;
    /** 来源渠道或系统。 */
    private String source;
    /** 创建时间。 */
    private String createdAt;
}
