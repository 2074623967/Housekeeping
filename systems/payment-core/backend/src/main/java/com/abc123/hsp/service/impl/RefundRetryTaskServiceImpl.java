package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.RefundRetryResultDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundRetryTaskService;
import com.abc123.hsp.service.RefundService;
import java.util.List;
import java.util.Collections;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 失败退款批次重试默认实现。
 */
@Service
public class RefundRetryTaskServiceImpl implements RefundRetryTaskService {

    private static final int DEFAULT_BATCH_SIZE = 100;
    private static final int MAX_BATCH_SIZE = 1000;
    private static final String TRANSACTION_FAILURE_CODE = "REFUND_RETRY_TRANSACTION_FAILED";

    private final RefundMapper refundMapper;
    private final RefundService refundService;
    private final TransactionTemplate transactionTemplate;
    private final int batchSize;

    @Autowired
    public RefundRetryTaskServiceImpl(RefundMapper refundMapper,
                                      RefundService refundService,
                                      PlatformTransactionManager transactionManager,
                                      @Value("${payment.refund-retry.batch-size:100}") int batchSize) {
        this.refundMapper = refundMapper;
        this.refundService = refundService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.batchSize = Math.min(Math.max(batchSize, 1), MAX_BATCH_SIZE);
    }

    RefundRetryTaskServiceImpl(RefundMapper refundMapper,
                               RefundService refundService,
                               PlatformTransactionManager transactionManager) {
        this(refundMapper, refundService, transactionManager, DEFAULT_BATCH_SIZE);
    }

    @Override
    public RefundRetryResultDTO retryFailedRefunds(String triggeredBy) {
        List<String> refundOrderIds = safeList(refundMapper.findFailedRefundOrderIds(batchSize));
        RefundRetryResultDTO result = new RefundRetryResultDTO();
        result.setProcessedCount(refundOrderIds.size());
        for (String refundOrderId : refundOrderIds) {
            try {
                executeRetryTransaction(refundOrderId, triggeredBy);
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

    private void executeRetryTransaction(String refundOrderId, String triggeredBy) {
        transactionTemplate.execute(status -> {
            executeRetryAction(refundOrderId, triggeredBy);
            return null;
        });
    }

    private void executeRetryAction(String refundOrderId, String triggeredBy) {
        try {
            refundService.retryForTask(refundOrderId, triggeredBy);
        } catch (RuntimeException exception) {
            throw exception instanceof SanitizedRefundRetryTransactionException
                    ? exception
                    : new SanitizedRefundRetryTransactionException(exception);
        }
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? Collections.emptyList() : values;
    }

    private static final class SanitizedRefundRetryTransactionException extends RuntimeException {

        private static final long serialVersionUID = 1L;

        private SanitizedRefundRetryTransactionException(RuntimeException exception) {
            super(TRANSACTION_FAILURE_CODE + ":" + exceptionName(exception), null, false, false);
        }

        private static String exceptionName(RuntimeException exception) {
            String simpleName = exception.getClass().getSimpleName();
            return simpleName.isEmpty() ? RuntimeException.class.getSimpleName() : simpleName;
        }
    }
}
