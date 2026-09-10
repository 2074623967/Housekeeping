package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentEventRetryResultDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
import com.abc123.hsp.service.PaymentEventRetryTaskService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

/**
 * 支付事件批次重发默认实现。
 */
@Service
public class PaymentEventRetryTaskServiceImpl implements PaymentEventRetryTaskService {

    private static final int OPERATOR_MAX_LENGTH = 64;

    private final PaymentEventMapper paymentEventMapper;
    private final PaymentEventDispatchService paymentEventDispatchService;
    private final TransactionTemplate transactionTemplate;

    public PaymentEventRetryTaskServiceImpl(PaymentEventMapper paymentEventMapper,
                                            PaymentEventDispatchService paymentEventDispatchService,
                                            PlatformTransactionManager transactionManager) {
        this.paymentEventMapper = paymentEventMapper;
        this.paymentEventDispatchService = paymentEventDispatchService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PaymentEventRetryResultDTO retryFailedEvents(boolean automatic, String operator) {
        String normalizedOperator = normalizeOperator(operator);
        List<String> eventNos = automatic
                ? paymentEventMapper.findDueFailedEventNos()
                : paymentEventMapper.findAllFailedEventNos();
        PaymentEventRetryResultDTO result = new PaymentEventRetryResultDTO();
        result.setProcessedCount(eventNos.size());
        for (String eventNo : eventNos) {
            try {
                Boolean success = transactionTemplate.execute(
                        status -> republishAndRecord(eventNo, normalizedOperator));
                if (Boolean.TRUE.equals(success)) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    recordFailure(result, eventNo);
                }
            } catch (RuntimeException exception) {
                recordFailure(result, eventNo);
            }
        }
        return result;
    }

    private String normalizeOperator(String operator) {
        if (!StringUtils.hasText(operator)) {
            throw new IllegalArgumentException("事件重发操作人不能为空");
        }
        for (int index = 0; index < operator.length(); index++) {
            if (Character.isISOControl(operator.charAt(index))) {
                throw new IllegalArgumentException("事件重发操作人不能包含控制字符");
            }
        }
        String normalized = operator.trim();
        if (normalized.length() > OPERATOR_MAX_LENGTH) {
            throw new IllegalArgumentException("事件重发操作人不能超过64个字符");
        }
        return normalized;
    }

    private boolean republishAndRecord(String eventNo, String operator) {
        boolean published = paymentEventDispatchService.republish(eventNo, operator);
        if (published && paymentEventMapper.updateLastRepublishAudit(eventNo, operator) != 1) {
            throw new IllegalStateException("payment event republish audit update failed");
        }
        return published;
    }

    private void recordFailure(PaymentEventRetryResultDTO result, String eventNo) {
        result.setFailCount(result.getFailCount() + 1);
        if (result.getFailedEventNos().size() < 3) {
            result.getFailedEventNos().add(eventNo);
        }
    }
}
