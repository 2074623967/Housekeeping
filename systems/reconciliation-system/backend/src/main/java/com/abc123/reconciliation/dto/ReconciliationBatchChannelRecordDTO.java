package com.abc123.reconciliation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 对账批次中的渠道记录。
 */
@Data
public class ReconciliationBatchChannelRecordDTO {

    /** 渠道交易流水号。 */
    private String channelTradeNo;
    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 渠道金额。 */
    private BigDecimal amount;
    /** 渠道交易状态。 */
    private String tradeStatus;
    /** 渠道交易时间。 */
    private LocalDateTime tradeTime;
}
