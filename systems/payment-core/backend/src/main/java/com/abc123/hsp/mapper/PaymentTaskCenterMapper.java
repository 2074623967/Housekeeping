package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentIssueAlertCandidateDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
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
    List<PaymentIssueAlertDispatchItemDTO> findPendingOutboxAlerts();

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
    List<PaymentIssueAlertLogEntity> findAcceptedIssueAlertDeliveryLogs();

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
     * 释放当前实例持有的任务租约锁。
     */
    int releaseTaskLease(@Param("taskCode") String taskCode,
                         @Param("lockOwner") String lockOwner);
}
