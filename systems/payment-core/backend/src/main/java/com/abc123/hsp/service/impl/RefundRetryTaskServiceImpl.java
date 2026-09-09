package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundRetryResultDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundRetryTaskService;
import com.abc123.hsp.service.RefundService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 失败退款批次重试默认实现。
 */
@Service
public class RefundRetryTaskServiceImpl implements RefundRetryTaskService {

    private final RefundMapper refundMapper;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;

    public RefundRetryTaskServiceImpl(RefundMapper refundMapper,
                                      RefundService refundService,
                                      PlatformTransactionManager transactionManager) {
        this.refundMapper = refundMapper;
        this.refundService = refundService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public RefundRetryResultDTO retryFailedRefunds(String triggeredBy) {
        List<String> refundOrderIds = refundMapper.findFailedRefundOrderIds();
        RefundRetryResultDTO result = new RefundRetryResultDTO();
        result.setProcessedCount(refundOrderIds.size());
        for (String refundOrderId : refundOrderIds) {
            try {
                transactionTemplate.execute(status -> {
                    refundService.retryForTask(refundOrderId, triggeredBy);
                    return null;
                });
                result.setSuccessCount(result.getSuccessCount() + 1);
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
