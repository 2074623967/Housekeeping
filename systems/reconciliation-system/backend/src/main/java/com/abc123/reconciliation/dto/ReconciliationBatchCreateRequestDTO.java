package com.abc123.reconciliation.dto;

import lombok.Data;

/**
 * 创建对账批次请求。
 */
@Data
public class ReconciliationBatchCreateRequestDTO {

    /** 业务日期，格式 yyyy-MM-dd。 */
    private String businessDate;
    /** 渠道编码。 */
    private String channelCode;
    /** 账单来源。 */
    private String billSource;
}

