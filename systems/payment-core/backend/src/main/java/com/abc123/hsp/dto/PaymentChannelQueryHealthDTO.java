package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付渠道短窗查单健康统计。
 */
@Data
public class PaymentChannelQueryHealthDTO {

    /** 渠道编码。 */
    private String channelCode;
    /** 统计窗口内查单次数。 */
    private Integer queryCount;
    /** 统计窗口内查单失败次数。 */
    private Integer failureCount;
}
