package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentQueryTrendPointDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;
import com.abc123.hsp.entity.PaymentIssueAlertLogEntity;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付任务中心 Mapper。
 */
public interface PaymentTaskCenterMapper {

    /**
     * 查询任务中心汇总指标。
     */
    PaymentTaskCenterOverviewDTO findOverviewSummary();

    /**
     * 统计当前可执行超时关单的支付单。
     */
    int countDueExpiredPayments();

    /**
     * 统计当前处于关单失败退避窗口的支付单。
     */
    int countBackingOffExpiredPayments();

    /**
     * 统计必须查单或人工核查的退款提交未知事件。
     */
    int countUnknownRefundSubmitEvents();

    /**
     * 查询最近任务执行日志。
     */
    List<PaymentTaskRunLogItemDTO> findRecentTaskRuns();

    /**
     * 查询任务执行日志列表。
     */
    List<PaymentTaskRunLogItemDTO> findTaskRunLogs(PaymentTaskRunLogQueryDTO query);

    /**
     * 查询任务执行日志导出列表。
     */
    List<PaymentTaskRunLogItemDTO> findTaskRunLogsForExport(PaymentTaskRunLogQueryDTO query);

    /**
     * 统计任务执行日志数量。
     */
    long countTaskRunLogs(PaymentTaskRunLogQueryDTO query);

    /**
     * 查询最新支付尝试达到等待阈值、尚未过期且仍待回调的支付单，供人工立即补查。
     */
    List<String> findPendingPaymentOrderIdsForManualQuery(@Param("minAgeSeconds") int minAgeSeconds,
                                                          @Param("perChannelBatchSize") int perChannelBatchSize,
                                                          @Param("batchSize") int batchSize);

    /**
     * 查询最新支付尝试达到自动重试退避窗口的待回调支付单。
     */
    List<String> findDuePendingPaymentOrderIdsForQuery(
            @Param("minAgeSeconds") int minAgeSeconds,
            @Param("retryIntervalSeconds") int retryIntervalSeconds,
            @Param("retryBackoffFactor") int retryBackoffFactor,
            @Param("maxRetryIntervalSeconds") int maxRetryIntervalSeconds,
            @Param("perChannelBatchSize") int perChannelBatchSize,
            @Param("batchSize") int batchSize);

    /**
     * 查询达到首次等待阈值的处理中退款，供人工立即补查。
     */
    List<String> findPendingRefundOrderIdsForManualQuery(@Param("minAgeSeconds") int minAgeSeconds,
                                                         @Param("perChannelBatchSize") int perChannelBatchSize,
                                                         @Param("batchSize") int batchSize);

    /**
     * 查询达到自动重试退避窗口的处理中退款。
     */
    List<String> findDuePendingRefundOrderIdsForQuery(
            @Param("minAgeSeconds") int minAgeSeconds,
            @Param("retryIntervalSeconds") int retryIntervalSeconds,
            @Param("retryBackoffFactor") int retryBackoffFactor,
            @Param("maxRetryIntervalSeconds") int maxRetryIntervalSeconds,
            @Param("perChannelBatchSize") int perChannelBatchSize,
            @Param("batchSize") int batchSize);

    /**
     * 统计最新支付尝试达到首次等待阈值的待回调支付单积压。
     */
    int countPendingPaymentQueryBacklog(@Param("minAgeSeconds") int minAgeSeconds);

    /**
     * 统计当前已达到自动查单退避时间的支付单。
     */
    int countDuePendingPaymentQueries(
            @Param("minAgeSeconds") int minAgeSeconds,
            @Param("retryIntervalSeconds") int retryIntervalSeconds,
            @Param("retryBackoffFactor") int retryBackoffFactor,
            @Param("maxRetryIntervalSeconds") int maxRetryIntervalSeconds);

    int countPendingRefundQueryBacklog(@Param("minAgeSeconds") int minAgeSeconds);

    int countDuePendingRefundQueries(
            @Param("minAgeSeconds") int minAgeSeconds,
            @Param("retryIntervalSeconds") int retryIntervalSeconds,
            @Param("retryBackoffFactor") int retryBackoffFactor,
            @Param("maxRetryIntervalSeconds") int maxRetryIntervalSeconds);

    /**
     * 统计最近窗口内的渠道查单次数。
     */
    int countRecentChannelQueries(@Param("lookbackHours") int lookbackHours);

    /**
     * 统计最近窗口内的渠道查单失败次数。
     */
    int countRecentChannelQueryFailures(@Param("lookbackHours") int lookbackHours);

    /**
     * 统计最近窗口内的渠道查单限流拒绝次数。
     */
    int countRecentChannelQueryRateLimitRejections(@Param("lookbackHours") int lookbackHours);

    /**
     * 统计冷却窗口内尚未确认的同类主动查单监控告警。
     */
    int countRecentUnacknowledgedQueryMonitorAlerts(@Param("issueType") String issueType,
                                                    @Param("cooldownMinutes") int cooldownMinutes);

    /**
     * 统计当前需要派发的来源 outbox 告警。
     */
    int countPendingIssueAlertDispatches();

    /**
     * 统计当前仍存在派发异常的来源 outbox 告警。
     */
    int countFailedIssueAlertDispatches();

    /**
     * 查询最老待派发来源 outbox 告警的等待分钟数。
     */
    int findOldestPendingIssueAlertDispatchMinutes();

    /**
     * 统计供应商已受理、等待回执回查的外发告警。
     */
    int countPendingIssueAlertReceipts();

    /**
     * 统计当前已达到自动回查时间的外发告警。
     */
    int countDueIssueAlertReceipts(@Param("minAgeSeconds") int minAgeSeconds,
                                   @Param("maxAttempts") int maxAttempts,
                                   @Param("claimTimeoutSeconds") int claimTimeoutSeconds);

    /**
     * 统计当前处于自动回查退避窗口的外发告警。
     */
    int countBackingOffIssueAlertReceipts(@Param("minAgeSeconds") int minAgeSeconds,
                                          @Param("maxAttempts") int maxAttempts,
                                          @Param("claimTimeoutSeconds") int claimTimeoutSeconds);

    /**
     * 统计当前已被回查任务有效占用的外发告警。
     */
    int countQueryingIssueAlertReceipts(@Param("claimTimeoutSeconds") int claimTimeoutSeconds);

    /**
     * 统计已耗尽自动回查次数的外发告警。
     */
    int countExhaustedIssueAlertReceipts(@Param("maxAttempts") int maxAttempts,
                                         @Param("claimTimeoutSeconds") int claimTimeoutSeconds);

    /**
     * 查询最老待回查外发告警的等待分钟数。
     */
    int findOldestPendingIssueAlertReceiptMinutes();

    /**
     * 查询最近窗口内的渠道查单小时趋势。
     */
    List<PaymentQueryTrendPointDTO> findRecentChannelQueryTrends(
            @Param("lookbackHours") int lookbackHours);

    /**
     * 统计已经超过 SLA 的支付交易异常数量。
     */
    int countOverduePaymentIssues();

    /**
     * 查询需要生成告警通知的超时异常。
     */
    List<PaymentIssueAlertCandidateDTO> findOverdueIssueAlertCandidates();

    /**
     * 查询超过升级阈值但仍未确认的异常告警。
     */
    List<PaymentIssueAlertCandidateDTO> findUnacknowledgedIssueAlertEscalationCandidates();

    /**
     * 查询待从站内 outbox 派发到真实通知通道的告警。
     */
    List<PaymentIssueAlertDispatchItemDTO> findPendingOutboxAlerts(@Param("batchSize") int batchSize);

    /**
     * 新增任务执行日志。
     */
    int insertTaskRunLog(PaymentTaskRunLogEntity entity);

    /**
     * 新增支付交易异常告警通知日志。
     */
    int insertIssueAlertLog(PaymentIssueAlertLogEntity entity);

    /**
     * 回写站内 outbox 告警的派发状态。
     */
    int updateIssueAlertDeliveryStatus(PaymentIssueAlertLogEntity entity);

    /**
     * 查询供应商已受理但尚未确认送达的异常告警派发日志。
     */
    List<PaymentIssueAlertLogEntity> findAcceptedIssueAlertDeliveryLogs(@Param("batchSize") int batchSize);

    /**
     * 查询指定的待确认供应商受理告警。
     */
    PaymentIssueAlertLogEntity findAcceptedIssueAlertDeliveryLogByAlertNo(@Param("alertNo") String alertNo);

    /**
     * 查询达到首次等待或重试退避时间、尚未耗尽自动次数的供应商受理告警。
     */
    List<PaymentIssueAlertLogEntity> findDueAcceptedIssueAlertDeliveryLogs(
            @Param("minAgeSeconds") int minAgeSeconds,
            @Param("maxAttempts") int maxAttempts,
            @Param("claimTimeoutSeconds") int claimTimeoutSeconds,
            @Param("batchSize") int batchSize);

    /**
     * 在外部查询前条件占用单条回查记录并递增尝试次数。
     */
    int claimIssueAlertReceiptQuery(@Param("alertNo") String alertNo,
                                    @Param("expectedAttemptCount") int expectedAttemptCount,
                                    @Param("manual") boolean manual,
                                    @Param("maxAttempts") int maxAttempts,
                                    @Param("claimTimeoutSeconds") int claimTimeoutSeconds);

    /**
     * 保存未送达或失败回查的有限结果及下一次自动重试时间。
     */
    int completeIssueAlertReceiptQueryAttempt(@Param("alertNo") String alertNo,
                                              @Param("attemptCount") int attemptCount,
                                              @Param("queryResult") String queryResult,
                                              @Param("nextDelaySeconds") int nextDelaySeconds,
                                              @Param("maxAttempts") int maxAttempts);

    /**
     * 回写异常告警供应商回执状态。
     */
    int updateIssueAlertProviderReceipt(PaymentIssueAlertLogEntity entity);

    /**
     * 基于供应商回执结果回写来源站内告警确认状态。
     */
    int updateSourceIssueAlertAcknowledgement(PaymentIssueAlertLogEntity entity);

    /**
     * 判断某个来源 outbox 在指定通知通道上是否已经成功派发过。
     */
    boolean hasSuccessfulIssueAlertChannelDelivery(String sourceAlertNo, String alertChannel);

    /**
     * 统计某个来源 outbox 在指定通道上的失败派发次数。
     */
    int countFailedIssueAlertChannelDeliveries(String sourceAlertNo, String alertChannel);

    /**
     * 查询某个来源 outbox 在指定通道上的最近一次派发日志。
     */
    PaymentIssueAlertLogEntity findLatestIssueAlertChannelDeliveryLog(String sourceAlertNo, String alertChannel);

    /**
     * 统计某个供应商在指定时间窗口内的派发次数。
     */
    int countIssueAlertProviderDeliveriesSince(@Param("providerCode") String providerCode,
                                               @Param("alertChannel") String alertChannel,
                                               @Param("sinceTime") String sinceTime);

    /**
     * 统计某个供应商在指定时间窗口内的失败派发次数。
     */
    int countIssueAlertProviderFailedDeliveriesSince(@Param("providerCode") String providerCode,
                                                     @Param("alertChannel") String alertChannel,
                                                     @Param("sinceTime") String sinceTime);

    /**
     * 查询指定通知通道当前启用中的供应商配置。
     */
    List<PaymentAlertProviderConfigDTO> findEnabledAlertProvidersByChannel(String alertChannel);

    /**
     * 初始化任务租约锁记录。
     */
    int initTaskLease(@Param("taskCode") String taskCode);

    /**
     * 抢占任务租约锁，避免多实例重复执行同一自动任务。
     */
    int acquireTaskLease(@Param("taskCode") String taskCode,
                         @Param("lockOwner") String lockOwner,
                         @Param("leaseSeconds") int leaseSeconds);

    /**
     * 仅允许当前有效租约的 owner 延长租期。
     */
    int renewTaskLease(@Param("taskCode") String taskCode,
                       @Param("lockOwner") String lockOwner,
                       @Param("leaseSeconds") int leaseSeconds);

    /**
     * 释放当前实例持有的任务租约锁。
     */
    int releaseTaskLease(@Param("taskCode") String taskCode,
                         @Param("lockOwner") String lockOwner);
}
