package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付趋势点位。
 */
@Data
public class PaymentTrendPointDTO {

    /** 统计日期。 */
    private String statDate;
    /** 当日总支付单量。 */
    private Integer totalCount;
    /** 当日成功支付单量。 */
    private Integer successCount;
    /** 当日支付成功金额。 */
    private String successAmount;
}
