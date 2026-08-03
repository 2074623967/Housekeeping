package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 账单中心总览指标。
 */
@Data
public class BillOverviewDTO {

    /** 账单总数。 */
    private Long totalBillCount;
    /** 已支付或已结清账单数。 */
    private Long paidBillCount;
    /** 待支付账单数。 */
    private Long unpaidBillCount;
    /** 部分支付账单数。 */
    private Long partialPaidBillCount;
    /** 已逾期未结清账单数。 */
    private Long overdueBillCount;
    /** 账单应收金额合计。 */
    private String totalBillAmount;
    /** 已付金额合计。 */
    private String totalPaidAmount;
    /** 待付金额合计。 */
    private String totalUnpaidAmount;
}
