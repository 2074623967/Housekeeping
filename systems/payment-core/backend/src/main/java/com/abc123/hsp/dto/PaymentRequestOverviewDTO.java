package com.abc123.hsp.dto;

import lombok.Data;

/**
 * 支付请求管理总览指标。
 */
@Data
public class PaymentRequestOverviewDTO {

    /** 请求总数。 */
    private Long totalRequestCount;
    /** 请求成功数。 */
    private Long successRequestCount;
    /** 请求失败数。 */
    private Long failedRequestCount;
    /** 处理中请求数。 */
    private Long processingRequestCount;
    /** 等待回调请求数。 */
    private Long waitingCallbackRequestCount;
    /** 涉及终端数。 */
    private Integer distinctTerminalCount;
    /** 涉及渠道数。 */
    private Integer distinctChannelCount;
    /** 重复支付单请求数。 */
    private Integer repeatedPaymentOrderCount;
    /** 缺响应报文请求数。 */
    private Integer missingResponseCount;
    /** 最近一次请求时间。 */
    private String latestRequestAt;
}
