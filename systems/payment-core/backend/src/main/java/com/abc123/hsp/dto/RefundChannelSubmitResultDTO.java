package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 退款渠道下单结果。
 */
@Data
public class RefundChannelSubmitResultDTO {

    /** 渠道退款单号。 */
    private String channelRefundNo;
    /** 渠道处理状态。 */
    private String status;
    /** 渠道处理状态样式。 */
    private String statusType;
    /** 返回报文。 */
    private String responsePayload;
}
