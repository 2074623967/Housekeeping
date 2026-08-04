package com.abc123.reconciliation.entity;

import java.time.LocalDate;
import lombok.Data;

/**
 * 对账批次实体，对应 t_reconciliation_batch。
 */
@Data
public class ReconciliationBatchEntity {

    /** 数据库主键。 */
    private Long id;
    /** 批次号。 */
    private String batchNo;
    /** 业务日期。 */
    private LocalDate businessDate;
    /** 渠道编码。 */
    private String channelCode;
    /** 账单来源。 */
    private String billSource;
    /** 批次状态。 */
    private String status;
}

