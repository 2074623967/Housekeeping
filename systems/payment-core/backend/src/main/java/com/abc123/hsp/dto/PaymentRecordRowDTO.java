package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付收款记录原型行模型。
 *
 * <p>字段按支付系统原型的收款管理列表定义，避免后台页面只展示支付单号和金额
 * 这类过度简化的信息。</p>
 */
@Data
public class PaymentRecordRowDTO {

    /** 序号。 */
    private Long serialNo;
    /** 支付单号。 */
    private String paymentOrderId;
    /** 业务订单号。 */
    private String businessOrderNo;
    /** 支付请求号。 */
    private String paymentRequestNo;
    /** 应用来源标识。 */
    private String applicationSourceId;
    /** 业务线标识。 */
    private String businessLineId;
    /** 外部交易流水号。 */
    private String externalTransactionNo;
    /** 支付网关。 */
    private String paymentGateway;
    /** 支付渠道。 */
    private String paymentChannel;
    /** 支付类型。 */
    private String paymentType;
    /** 支付状态。 */
    private String paymentStatus;
    /** 银行名称。 */
    private String bankName;
    /** 银行卡号脱敏值。 */
    private String cardNo;
    /** 渠道返回码。 */
    private String channelReturnCode;
    /** 返回参数类型。 */
    private String returnParameterType;
    /** 参数值。 */
    private String parameterValue;
    /** 有效期。 */
    private String validityPeriod;
    /** 支付金额。 */
    private String paymentAmount;
    /** 退款次数。 */
    private Integer refundCount;
    /** 已退款金额。 */
    private String refundedAmount;
    /** 商品名称。 */
    private String productName;
    /** 用户支付渠道标识。 */
    private String userPaymentChannelId;
    /** 收款账户。 */
    private String receivingAccount;
    /** 通知地址。 */
    private String notifyUrl;
    /** 回调消息主题。 */
    private String callbackMqTopic;
    /** 过期时间。 */
    private String expireTime;
    /** 创建时间。 */
    private String createdAt;
    /** 更新时间。 */
    private String updatedAt;
    /** 支付完成时间。 */
    private String paidAt;
    /** 用户标识。 */
    private String userId;
    /** 状态样式。 */
    private String statusType;
}
