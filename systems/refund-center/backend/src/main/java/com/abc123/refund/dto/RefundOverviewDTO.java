package com.abc123.refund.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 退款中心工作台摘要。
 */
@Data
public class RefundOverviewDTO {

    /** 退款单总数。 */
    private long totalCount;
    /** 审核中数量。 */
    private long reviewingCount;
    /** 处理中数量。 */
    private long processingCount;
    /** 失败数量。 */
    private long failCount;
    /** 成功退款金额。 */
    private BigDecimal successAmount;
}

