package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 待超时关闭的支付单。
 */
@Data
public class ExpiredPaymentDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 关联业务订单号。 */
    private String orderNo;
    /** 当前渠道交易号。 */
    private String channelTransactionNo;
}
