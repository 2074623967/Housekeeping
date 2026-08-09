package com.abc123.refund.dto;

import lombok.Data;

/**
 * 渠道退款异步回调请求。
 */
@Data
public class RefundCallbackRequestDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 渠道退款流水号。 */
    private String channelRefundId;
    /** 回调结果：SUCCESS 或 FAIL。 */
    private String result;
    /** 渠道失败码。 */
    private String failureCode;
    /** 渠道原始回执摘要。 */
    private String rawMessage;
}

