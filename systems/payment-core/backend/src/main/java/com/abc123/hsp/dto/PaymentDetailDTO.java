package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付单详情展示模型。
 */
@Data
public class PaymentDetailDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 预付单号。 */
    private String prepayOrderNo;
    /** 账单号。 */
    private String billNo;
    /** 订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 支付金额。 */
    private String amount;
    /** 支付方式。 */
    private String paymentMethod;
    /** 支付渠道。 */
    private String channel;
    /** 渠道交易号。 */
    private String channelTransactionNo;
    /** 最近一次请求终端。 */
    private String latestTerminal;
    /** 最近一次请求客户端 IP。 */
    private String latestClientIp;
    /** 最近一次幂等键。 */
    private String latestIdempotencyKey;
    /** 最近一次尝试状态。 */
    private String latestAttemptStatus;
    /** 最近一次尝试状态样式。 */
    private String latestAttemptStatusType;
    /** 最近一次请求报文。 */
    private String latestRequestPayload;
    /** 最近一次响应报文。 */
    private String latestResponsePayload;
    /** 查单结果来源。 */
    private String querySource;
    /** 支付状态。 */
    private String status;
    /** 支付状态样式。 */
    private String statusType;
    /** 创建时间。 */
    private String createdAt;
    /** 路由日志列表。 */
    private List<String> routeLogs;
    /** 回调日志列表。 */
    private List<String> notifyLogs;
    /** 事件日志列表。 */
    private List<String> eventLogs;
}
