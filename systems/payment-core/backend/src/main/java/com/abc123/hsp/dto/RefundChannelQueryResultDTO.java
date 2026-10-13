package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款渠道查单结果。
 */
@Data
public class RefundChannelQueryResultDTO {

    private String refundOrderId;
    private String refundAttemptNo;
    /** 渠道退款状态，例如 PROCESSING、SUCCESS、FAIL。 */
    private String refundStatus;
    /** 渠道侧退款单号。 */
    private String channelRefundNo;
    /** 查单来源。 */
    private String querySource;
}
