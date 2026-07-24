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
     * 判断某个异常在指定通知通道上是否已经成功派发过。
     */
    boolean hasSuccessfulIssueAlertChannelDelivery(String issueNo, String alertChannel);

    /**
     * 查询指定通知通道当前启用中的供应商配置。
     */
    PaymentAlertProviderConfigDTO findEnabledAlertProviderByChannel(String alertChannel);
}
