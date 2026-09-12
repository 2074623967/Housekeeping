package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付日终自动执行结果。
 */
@Data
public class PaymentDayEndAutoRunResultDTO {

    public static final String SKIP_REASON_EXISTING_BATCH = "EXISTING_BATCH";
    public static final String SKIP_REASON_LEASE_BUSY = "LEASE_BUSY";

    /** 是否生成了新的日终批次。 */
    private Boolean executed;
    /** 未执行原因编码。 */
    private String skipReason;
    /** 本轮目标业务日。 */
    private String bizDate;
    /** 本轮新生成的日终批次号，未执行时为空。 */
    private String batchNo;
    /** 新批次状态。 */
    private String batchStatus;
}
