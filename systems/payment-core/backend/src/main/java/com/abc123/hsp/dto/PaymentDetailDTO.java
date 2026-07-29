package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付单详情展示模型，聚合支付单基础信息、最近一次支付尝试快照以及路由/回调/事件轨迹。
 */
@Data
public class PaymentDetailDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 当前支付单关联的预付单号。 */
    private String prepayOrderNo;
    /** 当前支付单关联的账单号。 */
    private String billNo;
    /** 当前支付单关联的业务订单号。 */
    private String orderNo;
    /** 付款客户名称。 */
    private String customerName;
    /** 支付单应收金额。 */
    private String amount;
    /** 用户本次选择的支付方式。 */
    private String paymentMethod;
    /** 实际命中的支付渠道。 */
    private String channel;
    /** 渠道侧返回的交易流水号。 */
    private String channelTransactionNo;
    /** 最近一次支付尝试的请求终端。 */
    private String latestTerminal;
    /** 最近一次支付尝试的客户端 IP。 */
    private String latestClientIp;
    /** 最近一次支付尝试使用的幂等键。 */
    private String latestIdempotencyKey;
    /** 最近一次支付尝试的状态。 */
    private String latestAttemptStatus;
    /** 最近一次支付尝试状态对应的前端展示样式。 */
    private String latestAttemptStatusType;
    /** 最近一次支付请求报文快照。 */
    private String latestRequestPayload;
    /** 最近一次支付响应报文快照。 */
    private String latestResponsePayload;
    /** 最近一次查单结果来源。 */
    private String querySource;
    /** 当前支付单状态。 */
    private String status;
    /** 当前支付单状态对应的前端展示样式。 */
    private String statusType;
    /** 支付单创建时间。 */
    private String createdAt;
    /** 支付路由执行轨迹。 */
    private List<String> routeLogs;
    /** 支付回调处理轨迹。 */
    private List<String> notifyLogs;
    /** 支付领域事件轨迹。 */
    private List<String> eventLogs;
}
