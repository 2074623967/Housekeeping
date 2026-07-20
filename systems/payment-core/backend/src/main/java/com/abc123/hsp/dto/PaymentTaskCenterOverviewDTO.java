package com.abc123.hsp.dto;

import java.util.List;
import lombok.Data;

/**
 * 支付任务中心总览。
 */
@Data
public class PaymentTaskCenterOverviewDTO {

    /** 待超时关闭支付单数。 */
    private Integer expiredPaymentCount;
    /** 待收口支付中单数。 */
    private Integer pendingCallbackCount;
    /** 失败事件数。 */
    private Integer failedEventCount;
    /** 失败退款数。 */
    private Integer failedRefundCount;
    /** 日终告警批次数。 */
    private Integer warningDayEndBatchCount;
    /** 当前重点任务告警。 */
    private List<PaymentAlertItemDTO> focusAlerts;
    /** 最近任务执行日志。 */
    private List<PaymentTaskRunLogItemDTO> recentTaskRuns;
}
