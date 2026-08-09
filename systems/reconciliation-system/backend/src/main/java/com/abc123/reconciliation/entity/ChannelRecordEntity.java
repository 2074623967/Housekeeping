package com.abc123.reconciliation.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 渠道账单记录实体。
 */
@Data
public class ChannelRecordEntity {

    /** 数据库主键。 */
    private Long id;
    /** 批次号。 */
    private String batchNo;
    /** 渠道交易流水号。 */
    private String channelTradeNo;
    /** 平台支付单号。 */
    private String paymentOrderId;
    /** 渠道金额。 */
    private BigDecimal amount;
    /** 渠道状态。 */
    private String tradeStatus;
    /** 渠道交易时间。 */
    private LocalDateTime tradeTime;
}

