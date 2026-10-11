package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.BlockedExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogItemDTO;
import com.abc123.hsp.dto.PaymentTaskRunLogQueryDTO;

/**
 * 支付任务中心服务。
 */
public interface PaymentTaskCenterService {

    /**
     * 查询支付任务中心总览。
     */
    PaymentTaskCenterOverviewDTO overview();

    PageResultDTO<BlockedExpiredPaymentDTO> listBlockedExpiredPayments(int pageNo, int pageSize);

    /**
     * 查询任务执行日志。
     */
    PageResultDTO<PaymentTaskRunLogItemDTO> listTaskRuns(PaymentTaskRunLogQueryDTO query);

    PaymentTaskRunLogItemDTO taskRunDetail(String taskLogNo);

    /**
     * 导出任务执行日志，供运营、财务和值班复盘留痕。
     */
    String exportTaskRunsCsv(PaymentTaskRunLogQueryDTO query);

    /**
     * 手动执行超时关单任务。
     */
    PaymentTaskActionResultDTO runCloseExpiredPayments(String operator);

    /**
     * 自动执行超时关单任务。
     */
    PaymentTaskActionResultDTO runAutoCloseExpiredPayments();

    /**
     * 自动执行支付日终任务并写入统一任务日志。
     */
    PaymentTaskActionResultDTO runAutoPaymentDayEnd();

    /**
     * 自动巡检前一业务日日终批次与成功创建日志。
     */
    PaymentTaskActionResultDTO runAutoPaymentDayEndPatrol();

    /**
     * 手动补查等待回调的支付单。
     */
    PaymentTaskActionResultDTO runPendingPaymentQueries(String operator);

    /**
     * 自动补查等待回调的支付单。
     */
    PaymentTaskActionResultDTO runAutoPendingPaymentQueries();

    /**
     * 手动补查处理中的退款单。
     */
    PaymentTaskActionResultDTO runPendingRefundQueries(String operator);

    /**
     * 自动补查处理中的退款单。
     */
    PaymentTaskActionResultDTO runAutoPendingRefundQueries();

    /**
     * 手动生成主动查单监控告警。
     */
    PaymentTaskActionResultDTO runGenerateQueryMonitorAlerts(String operator);

    /**
     * 自动生成主动查单监控告警。
     */
    PaymentTaskActionResultDTO runAutoGenerateQueryMonitorAlerts();

    /**
     * 手动执行失败事件重发。
     */
    PaymentTaskActionResultDTO runRepublishFailedEvents(String operator);

    /**
     * 自动执行失败事件重发。
     */
    PaymentTaskActionResultDTO runAutoRepublishFailedEvents();

    /**
     * 手动执行失败退款重试。
     */
    PaymentTaskActionResultDTO runRetryFailedRefunds(String operator);

    /**
     * 自动执行失败退款重试。
     */
    PaymentTaskActionResultDTO runAutoRetryFailedRefunds();

    /**
     * 手动执行异常 SLA 升级巡检。
     */
    PaymentTaskActionResultDTO runEscalateOverdueIssues(String operator);

    /**
     * 手动执行异常告警派发。
     */
    PaymentTaskActionResultDTO runDispatchIssueAlerts(String operator);

    /**
     * 自动执行异常 SLA 升级巡检。
     */
    PaymentTaskActionResultDTO runAutoEscalateOverdueIssues();

    /**
     * 自动执行异常告警派发。
     */
    PaymentTaskActionResultDTO runAutoDispatchIssueAlerts();

    /**
     * 手动执行异常告警供应商回执回查。
     */
    PaymentTaskActionResultDTO runReconcileIssueAlertReceipts(String operator);

    /**
     * 手动执行指定异常告警的供应商回执回查。
     */
    PaymentTaskActionResultDTO runReconcileIssueAlertReceipt(String alertNo, String operator);

    /**
     * 自动执行异常告警供应商回执回查。
     */
    PaymentTaskActionResultDTO runAutoReconcileIssueAlertReceipts();

    /**
     * 手动执行支付控制策略自动巡检。
     */
    PaymentTaskActionResultDTO runControlPolicySelfChecks(String operator);

    /**
     * 自动执行支付控制策略自动巡检。
     */
    PaymentTaskActionResultDTO runAutoControlPolicySelfChecks();
}
