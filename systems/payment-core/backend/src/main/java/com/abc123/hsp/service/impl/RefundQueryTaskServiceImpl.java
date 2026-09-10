package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryTaskResultDTO;
import com.abc123.hsp.dto.RefundStatusQueryRequestDTO;
import com.abc123.hsp.mapper.PaymentTaskCenterMapper;
import com.abc123.hsp.service.RefundQueryTaskService;
import com.abc123.hsp.service.RefundService;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 处理中退款主动查单任务默认实现。
 */
@Service
public class RefundQueryTaskServiceImpl implements RefundQueryTaskService {

    private final PaymentTaskCenterMapper paymentTaskCenterMapper;
    private final RefundService refundService;
    private final int minAgeSeconds;
    private final int retryIntervalSeconds;
    private final int retryBackoffFactor;
    private final int maxRetryIntervalSeconds;
    private final int perChannelBatchSize;
    private final int batchSize;

    public RefundQueryTaskServiceImpl(
            PaymentTaskCenterMapper paymentTaskCenterMapper,
            RefundService refundService,
            @Value("${payment.refund-query.compensation.min-age-seconds:30}") int minAgeSeconds,
            @Value("${payment.refund-query.compensation.retry-interval-seconds:60}") int retryIntervalSeconds,
            @Value("${payment.refund-query.compensation.retry-backoff-factor:2}") int retryBackoffFactor,
            @Value("${payment.refund-query.compensation.max-retry-interval-seconds:900}") int maxRetryIntervalSeconds,
            @Value("${payment.refund-query.compensation.per-channel-batch-size:20}") int perChannelBatchSize,
            @Value("${payment.refund-query.compensation.batch-size:100}") int batchSize) {
        this.paymentTaskCenterMapper = paymentTaskCenterMapper;
        this.refundService = refundService;
        this.minAgeSeconds = Math.max(minAgeSeconds, 0);
        this.retryIntervalSeconds = Math.max(retryIntervalSeconds, 1);
        this.retryBackoffFactor = Math.max(retryBackoffFactor, 1);
        this.maxRetryIntervalSeconds = Math.max(maxRetryIntervalSeconds, this.retryIntervalSeconds);
        this.perChannelBatchSize = Math.max(perChannelBatchSize, 1);
        this.batchSize = Math.max(batchSize, 1);
    }

    @Override
    public RefundQueryTaskResultDTO queryPendingRefunds() {
        return query(paymentTaskCenterMapper.findPendingRefundOrderIdsForManualQuery(
                minAgeSeconds, perChannelBatchSize, batchSize));
    }

    @Override
    public RefundQueryTaskResultDTO queryDueRefunds() {
        return query(paymentTaskCenterMapper.findDuePendingRefundOrderIdsForQuery(
                minAgeSeconds,
                retryIntervalSeconds,
                retryBackoffFactor,
                maxRetryIntervalSeconds,
                perChannelBatchSize,
                batchSize));
    }

    @Override
    public int countPendingRefunds() {
        return paymentTaskCenterMapper.countPendingRefundQueryBacklog(minAgeSeconds);
    }

    @Override
    public int countDueRefunds() {
        return paymentTaskCenterMapper.countDuePendingRefundQueries(
                minAgeSeconds, retryIntervalSeconds, retryBackoffFactor, maxRetryIntervalSeconds);
    }

    private RefundQueryTaskResultDTO query(List<String> refundOrderIds) {
        RefundQueryTaskResultDTO result = new RefundQueryTaskResultDTO();
        result.setProcessedCount(refundOrderIds.size());
        for (String refundOrderId : refundOrderIds) {
            RefundStatusQueryRequestDTO request = new RefundStatusQueryRequestDTO();
            request.setRefundOrderId(refundOrderId);
            try {
                RefundListItemDTO refund = refundService.query(request);
                if (refund != null && "SUCCESS".equals(refund.getStatus())) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setWarningCount(result.getWarningCount() + 1);
                }
            } catch (RuntimeException exception) {
                result.setFailCount(result.getFailCount() + 1);
                if (result.getFailedRefundOrderIds().size() < 3) {
                    result.getFailedRefundOrderIds().add(refundOrderId);
                }
            }
        }
        return result;
    }
}
