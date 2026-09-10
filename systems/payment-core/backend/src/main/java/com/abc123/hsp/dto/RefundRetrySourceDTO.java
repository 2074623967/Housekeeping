package com.abc123.hsp.dto;

import java.math.BigDecimal;
import lombok.Data;

/**
 * 失败退款重试前的额度校验快照。
 */
@Data
public class RefundRetrySourceDTO {

    /** 退款单号。 */
    private String refundOrderId;
    /** 原支付单号。 */
    private String paymentOrderId;
    /** 退款金额。 */
    private BigDecimal refundAmount;
    /** 当前退款状态。 */
    private String status;
}
