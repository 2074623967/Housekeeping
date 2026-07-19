package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付处理日志查询条件。
 */
@Data
public class PaymentLogQueryDTO {

    /** 支付单号。 */
    private String paymentOrderId;
    /** 处理阶段。 */
    private String processStage = "全部";
    /** 日志级别。 */
    private String logLevel = "全部";
}
