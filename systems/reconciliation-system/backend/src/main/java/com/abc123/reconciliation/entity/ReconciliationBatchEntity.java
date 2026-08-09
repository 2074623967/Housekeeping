package com.abc123.reconciliation.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
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
    /** 对账准入状态。 */
    private String readinessStatus;
    /** 对账准入结论。 */
    private String readinessSummary;
    /** 对账准入建议动作。 */
    private String readinessSuggestedAction;
    /** 对账准入阻断责任方。 */
    private String readinessBlockingOwner;
    /** 纳入准入判断的业务链路数。 */
    private int readinessChainCount;
    /** 已完成关键事实链路数。 */
    private int readinessCompletedChainCount;
    /** 存在异常事实的链路数。 */
    private int readinessAbnormalChainCount;
    /** 尚未完成关键事实的链路数。 */
    private int readinessPendingChainCount;
    /** 最近一次准入检查时间。 */
    private LocalDateTime readinessCheckedAt;
}
