package com.abc123.hsp.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 退款主动查单批次结果。
 */
@Data
public class RefundQueryTaskResultDTO {

    private int processedCount;
    private int successCount;
    private int warningCount;
    private int failCount;
    private List<String> failedRefundOrderIds = new ArrayList<String>();
}
