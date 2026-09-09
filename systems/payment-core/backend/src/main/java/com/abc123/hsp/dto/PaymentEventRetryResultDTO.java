package com.abc123.hsp.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 支付事件批次重发结果。
 */
@Data
public class PaymentEventRetryResultDTO {

    private int processedCount;
    private int successCount;
    private int failCount;
    private List<String> failedEventNos = new ArrayList<String>();
}
