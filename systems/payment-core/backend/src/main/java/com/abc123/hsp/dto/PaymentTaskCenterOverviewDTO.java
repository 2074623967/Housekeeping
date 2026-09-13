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
    /** 当前已达到超时关单重试时间的支付单数。 */
    private Integer dueExpiredPaymentCount;
    /** 当前处于超时关单失败退避窗口的支付单数。 */
    private Integer backingOffExpiredPaymentCount;
    /** 待收口支付中单数。 */
    private Integer pendingCallbackCount;
    /** 已达到主动补查阈值的支付单数。 */
    private Integer pendingQueryBacklogCount;
    /** 当前已达到自动退避时间的支付单数。 */
    private Integer duePendingQueryCount;
    /** 已达到主动补查阈值的处理中退款数。 */
    private Integer pendingRefundQueryBacklogCount;
    /** 当前已达到自动退避时间的处理中退款数。 */
    private Integer duePendingRefundQueryCount;
    /** 查单趋势统计窗口小时数。 */
    private Integer queryMonitorWindowHours;
    /** 统计窗口内查单总次数。 */
    private Integer recentQueryCount;
    /** 统计窗口内查单失败次数。 */
    private Integer recentQueryFailureCount;
    /** 统计窗口内查单失败率。 */
    private String recentQueryFailureRate;
    /** 查单失败笔数告警阈值。 */
    private Integer queryFailureCountThreshold;
    /** 查单失败率告警阈值。 */
    private Integer queryFailureRateThresholdPercent;
    /** 失败率告警最小样本数。 */
    private Integer queryFailureRateMinimumSampleCount;
    /** 统计窗口内渠道查单限流拒绝次数。 */
    private Integer recentQueryRateLimitRejectionCount;
    /** 渠道查单限流拒绝告警阈值。 */
    private Integer queryRateLimitRejectionCountThreshold;
    /** 当前待派发的来源 outbox 告警数。 */
    private Integer pendingIssueAlertDispatchCount;
    /** 当前仍处于部分失败或派发失败的来源 outbox 告警数。 */
    private Integer failedIssueAlertDispatchCount;
    /** 最老待派发来源 outbox 告警的等待分钟数。 */
    private Integer oldestPendingIssueAlertDispatchMinutes;
    /** 待派发数量 P1 告警阈值。 */
    private Integer issueAlertDispatchCountThreshold;
    /** 待派发时长 P1 告警阈值。 */
    private Integer issueAlertDispatchTimeoutMinutes;
    /** 供应商已受理、等待回执回查的外发告警数。 */
    private Integer pendingIssueAlertReceiptCount;
    /** 当前可由人工批量执行回执回查的外发告警数。 */
    private Integer manualIssueAlertReceiptCount;
    /** 当前已达到自动回查时间的外发告警数。 */
    private Integer dueIssueAlertReceiptCount;
    /** 当前处于自动回查退避窗口的外发告警数。 */
    private Integer backingOffIssueAlertReceiptCount;
    /** 当前已被回查任务占用且占用未超时的外发告警数。 */
    private Integer queryingIssueAlertReceiptCount;
    /** 已耗尽自动回查次数的外发告警数。 */
    private Integer exhaustedIssueAlertReceiptCount;
    /** 最老待回查外发告警的等待分钟数。 */
    private Integer oldestPendingIssueAlertReceiptMinutes;
    /** 待回查数量 P1 告警阈值。 */
    private Integer issueAlertReceiptCountThreshold;
    /** 待回查时长 P1 告警阈值。 */
    private Integer issueAlertReceiptTimeoutMinutes;
    /** 最近查单小时趋势。 */
    private List<PaymentQueryTrendPointDTO> queryTrends;
    /** 失败与死信事件总数。 */
    private Integer failedEventCount;
    /** 当前可由人工批量重发的失败事件数。 */
    private Integer retryableFailedEventCount;
    /** 已停止批量重试、需要人工处置的死信事件数。 */
    private Integer deadLetterEventCount;
    /** 退款提交结果未知、禁止直接重试的事件数。 */
    private Integer unknownRefundSubmitEventCount;
    /** 失败退款数。 */
    private Integer failedRefundCount;
    /** 终态事实完整、可批量重试的失败退款数。 */
    private Integer retryableFailedRefundCount;
    /** 终态事实异常、禁止批量重试的失败退款数。 */
    private Integer blockedFailedRefundCount;
    /** 日终告警批次数。 */
    private Integer warningDayEndBatchCount;
    /** 当前启用、可执行批量自检的支付控制策略数。 */
    private Integer enabledControlPolicyCount;
    /** 支付控制策略异常数。 */
    private Integer controlPolicyWarningCount;
    /** SLA 超时异常数。 */
    private Integer overdueIssueCount;
    /** 当前重点任务告警。 */
    private List<PaymentAlertItemDTO> focusAlerts;
    /** 最近任务执行日志。 */
    private List<PaymentTaskRunLogItemDTO> recentTaskRuns;
}
