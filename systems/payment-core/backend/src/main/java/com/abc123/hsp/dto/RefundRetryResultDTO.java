package com.abc123.hsp.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 失败退款批次重试结果。
 */
@Data
public class RefundRetryResultDTO {

    private int processedCount;
    private int successCount;
    private int failCount;
    private List<String> failedRefundOrderIds = new ArrayList<String>();
}
