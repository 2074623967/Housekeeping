package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;
import com.abc123.hsp.entity.PaymentTaskRunLogEntity;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import com.abc123.hsp.service.PaymentTaskCenterService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付任务中心服务默认实现。
 */
@Service
public class PaymentTaskCenterServiceImpl implements PaymentTaskCenterService {

    private static final String RUN_MODE_MANUAL = "MANUAL";
    private static final String TASK_STATUS_SUCCESS = "SUCCESS";
    private static final String TASK_STATUS_WARN = "WARNING";

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final PaymentExpiryTaskService paymentExpiryTaskService;
    private final PaymentEventMapper paymentEventMapper;
    private final RefundMapper refundMapper;

    public PaymentTaskCenterServiceImpl(PaymentTaskCenterMapper paymentTaskCenterMapper,
                                        PaymentExpiryTaskService paymentExpiryTaskService,
                                        PaymentEventMapper paymentEventMapper,
                                        RefundMapper refundMapper) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.paymentExpiryTaskService = paymentExpiryTaskService;
        this.paymentEventMapper = paymentEventMapper;
        this.refundMapper = refundMapper;
    }

    @Override
    public PaymentTaskCenterOverviewDTO overview() {
        PaymentTaskCenterOverviewDTO overview = paymentTaskCenterMapper.findOverviewSummary();
        overview.setRecentTaskRuns(paymentTaskCenterMapper.findRecentTaskRuns());
        return overview;
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runCloseExpiredPayments() {
        int successCount = paymentExpiryTaskService.closeExpiredPayments();
        int processedCount = successCount;
        return buildAndRecordResult(
                "PAYMENT_EXPIRE_CLOSE",
                "支付超时关单",
                processedCount,
                successCount,
                0,
                successCount == 0 ? "当前没有待关闭的超时支付单。" : String.format("已关闭 %d 笔超时支付单。", successCount)
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRepublishFailedEvents() {
        List<String> failedEventNos = paymentEventMapper.findFailedEventNos();
        int successCount = 0;
        for (String eventNo : failedEventNos) {
            successCount += paymentEventMapper.markRepublished(eventNo);
        }
        int failCount = Math.max(failedEventNos.size() - successCount, 0);
        return buildAndRecordResult(
                "PAYMENT_EVENT_RETRY",
                "失败事件重发",
                failedEventNos.size(),
                successCount,
                failCount,
                failedEventNos.isEmpty()
                        ? "当前没有失败事件需要重发。"
                        : String.format("已重发 %d 条失败事件，剩余失败 %d 条。", successCount, failCount)
        );
    }

    @Override
    @Transactional
    public PaymentTaskActionResultDTO runRetryFailedRefunds() {
        List<String> failedRefundOrderIds = refundMapper.findFailedRefundOrderIds();
        int successCount = 0;
        for (String refundOrderId : failedRefundOrderIds) {
            successCount += refundMapper.updateRefundStatus(refundOrderId, "FAIL", "PROCESSING", "warn", false);
        }
        int failCount = Math.max(failedRefundOrderIds.size() - successCount, 0);
        return buildAndRecordResult(
                "REFUND_FAIL_RETRY",
                "失败退款重试",
                failedRefundOrderIds.size(),
                successCount,
                failCount,
                failedRefundOrderIds.isEmpty()
                        ? "当前没有失败退款需要重试。"
                        : String.format("已重试 %d 笔失败退款，剩余失败 %d 笔。", successCount, failCount)
        );
    }

    private PaymentTaskActionResultDTO buildAndRecordResult(String taskCode,
                                                            String taskName,
                                                            int processedCount,
                                                            int successCount,
                                                            int failCount,
                                                            String summaryComment) {
        PaymentTaskRunLogEntity entity = new PaymentTaskRunLogEntity();
        entity.setTaskLogNo(buildTaskLogNo());
        entity.setTaskCode(taskCode);
        entity.setTaskName(taskName);
        entity.setRunMode(RUN_MODE_MANUAL);
        entity.setTaskStatus(failCount > 0 ? TASK_STATUS_WARN : TASK_STATUS_SUCCESS);
        entity.setTaskStatusType(failCount > 0 ? "warn" : "success");
        entity.setProcessedCount(processedCount);
        entity.setSuccessCount(successCount);
        entity.setFailCount(failCount);
        entity.setSummaryComment(summaryComment);
        entity.setTriggeredBy("payment-core-admin");
        paymentTaskCenterMapper.insertTaskRunLog(entity);

        PaymentTaskActionResultDTO result = new PaymentTaskActionResultDTO();
        result.setTaskCode(taskCode);
        result.setTaskName(taskName);
        result.setProcessedCount(processedCount);
        result.setSuccessCount(successCount);
        result.setFailCount(failCount);
        result.setSummaryComment(summaryComment);
        result.setOverview(overview());
        return result;
    }

    private String buildTaskLogNo() {
        return "PTL" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}
