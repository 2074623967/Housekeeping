package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付日终巡检结果。
 */
@Data
public class PaymentDayEndPatrolResultDTO {

    /** 是否已取得业务日租约并完成批次检查。 */
    private Boolean inspected;
    /** 未检查原因，仅允许 LEASE_BUSY。 */
    private String skipReason;
    /** 目标业务日。 */
    private String bizDate;
    /** 目标业务日全部批次数。 */
    private Integer batchCount;
    /** 目标业务日自动批次数。 */
    private Integer autoBatchCount;
}
