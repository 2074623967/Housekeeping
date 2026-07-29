package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款渠道下单请求。
 */
@Data
public class RefundChannelSubmitRequestDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 原订单号。 */
    private String orderNo;
    /** 客户名称。 */
    private String customerName;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 退款方式。 */
    private String refundMethod;
    /** 退款原因。 */
    private String refundReason;
    /** 渠道编码。 */
    private String channelCode;
}
