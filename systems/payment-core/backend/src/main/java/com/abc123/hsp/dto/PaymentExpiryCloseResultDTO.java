package com.abc123.hsp.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

/**
 * 支付超时关单批次结果。
 */
@Data
public class PaymentExpiryCloseResultDTO {

    private int processedCount;
    private int successCount;
    private int skippedCount;
    private int failCount;
    private List<String> failedPaymentOrderIds = new ArrayList<String>();
}
