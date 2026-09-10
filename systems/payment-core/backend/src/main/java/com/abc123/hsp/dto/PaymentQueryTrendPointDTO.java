package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付与退款渠道查单小时趋势点位。
 */
@Data
public class PaymentQueryTrendPointDTO {

    /** 小时统计桶。 */
    private String timeBucket;
    /** 查单总次数。 */
    private Integer queryCount;
    /** 查单失败次数。 */
    private Integer failureCount;
    /** 查单失败率。 */
    private String failureRate;
}
