package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 渠道查单适配器返回结果。
 */
@Data
public class PaymentChannelQueryResultDTO {

    /** 渠道状态，例如 SUCCESS、WAIT_CALLBACK、FAIL。 */
    private String tradeStatus;
    /** 渠道交易流水号。 */
    private String channelTransactionNo;
    /** 查单来源，例如 WECHAT_SIMULATION、ALIPAY_SIMULATION、LOCAL_SIMULATION_FALLBACK。 */
    private String querySource;
}
