package com.abc123.reconciliation.dto;

import java.time.LocalDate;
import java.util.List;
import lombok.Data;

/**
 * 对账批次详情。
 */
@Data
public class ReconciliationBatchDetailDTO {

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
    /** 渠道记录数。 */
    private int channelCount;
    /** 平台记录数。 */
    private int internalCount;
    /** 已匹配数。 */
    private int matchedCount;
    /** 差异数。 */
    private int differenceCount;
    /** 未结案差异数。 */
    private int openDifferenceCount;
    /** 已结案差异数。 */
    private int resolvedDifferenceCount;
    /** 渠道账单记录。 */
    private List<ReconciliationBatchChannelRecordDTO> channelRecords;
    /** 平台支付事实记录。 */
    private List<ReconciliationBatchInternalRecordDTO> internalRecords;
    /** 差异明细。 */
    private List<ReconciliationDifferenceDTO> differences;
}
