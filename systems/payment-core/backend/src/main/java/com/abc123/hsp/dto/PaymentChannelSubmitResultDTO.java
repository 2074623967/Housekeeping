package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道下单结果。
 */
@Data
public class PaymentChannelSubmitResultDTO {

    /** 渠道交易流水号。 */
    private String channelTransactionNo;
    /** 支付尝试状态。 */
    private String attemptStatus;
    /** 支付尝试状态类型。 */
    private String attemptStatusType;
    /** 渠道响应留痕。 */
    private String responsePayload;
}
