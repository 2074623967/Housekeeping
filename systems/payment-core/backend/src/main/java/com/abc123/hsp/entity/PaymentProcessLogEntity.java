package com.abc123.hsp.entity;

import lombok.Data;

/**
 * 支付处理日志实体，保存一次支付处理节点的不可变事实快照。
 */
@Data
public class PaymentProcessLogEntity {

    /** 日志编号。 */
    private String logNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 订单号。 */
    private String orderNo;
    /** 支付请求号，可为空。 */
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
    /** 日志消息。 */
    private String message;
    /** 来源渠道或系统。 */
    private String source;
}
