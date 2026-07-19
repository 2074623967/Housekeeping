package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付处理日志列表项，统一展示支付主链路的处理节点和结果。
 */
@Data
public class PaymentLogListItemDTO {

    /** 日志编号。 */
    private String logNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 处理阶段。 */
    private String processStage;
    /** 日志级别。 */
    private String logLevel;
    /** 日志级别样式。 */
    private String logLevelType;
    /** 日志消息。 */
    private String message;
    /** 来源渠道。 */
    private String source;
    /** 创建时间。 */
    private String createdAt;
}
