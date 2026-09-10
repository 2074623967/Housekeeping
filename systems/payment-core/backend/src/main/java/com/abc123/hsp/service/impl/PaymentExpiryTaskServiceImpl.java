package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 支付超时关单任务服务默认实现。
 */
@Service
public class PaymentExpiryTaskServiceImpl implements PaymentExpiryTaskService {

    private final PaymentMapper paymentMapper;
    private final TransactionTemplate transactionTemplate;

    public PaymentExpiryTaskServiceImpl(PaymentMapper paymentMapper,
                                        PlatformTransactionManager transactionManager) {
        this.paymentMapper = paymentMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public PaymentExpiryCloseResultDTO closeExpiredPayments() {
        List<ExpiredPaymentDTO> expiredPayments = paymentMapper.findExpiredPayments();
        PaymentExpiryCloseResultDTO result = new PaymentExpiryCloseResultDTO();
        result.setProcessedCount(expiredPayments.size());
        for (ExpiredPaymentDTO payment : expiredPayments) {
            try {
                Boolean closed = transactionTemplate.execute(status -> closeExpiredPayment(payment));
                if (Boolean.TRUE.equals(closed)) {
                    result.setSuccessCount(result.getSuccessCount() + 1);
                } else {
                    result.setSkippedCount(result.getSkippedCount() + 1);
                }
            } catch (RuntimeException exception) {
                result.setFailCount(result.getFailCount() + 1);
                if (result.getFailedPaymentOrderIds().size() < 3) {
                    result.getFailedPaymentOrderIds().add(payment.getPaymentOrderId());
                }
            }
        }
        return result;
    }

    private boolean closeExpiredPayment(ExpiredPaymentDTO payment) {
        int closedRows = paymentMapper.markExpiredUnsubmittedPaymentClosed(payment.getPaymentOrderId());
        if (closedRows == 0) {
            return false;
        }
        paymentMapper.updatePaymentAttemptStatusByPaymentOrderId(payment.getPaymentOrderId(), "已关闭", "danger");
        paymentMapper.updatePrepayStatusByPaymentOrderId(payment.getPaymentOrderId(), "已失效", "danger");
        String eventNo = "EVT" + System.currentTimeMillis()
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String eventPayload = "{\"source\":\"scheduler\",\"reason\":\"PREPAY_EXPIRED\"}";
        paymentMapper.insertEvent(eventNo, "PAYMENT_EXPIRED_CLOSED", payment.getPaymentOrderId(),
                payment.getOrderNo(), eventPayload);
        PaymentProcessLogEntity processLog = new PaymentProcessLogEntity();
        processLog.setLogNo(eventNo);
        processLog.setPaymentOrderId(payment.getPaymentOrderId());
        processLog.setOrderNo(payment.getOrderNo());
        processLog.setSourceType("PAYMENT_EVENT");
        processLog.setSourceNo(eventNo);
        processLog.setProcessStage("业务事件");
        processLog.setLogLevel("INFO");
        processLog.setLogLevelType("info");
        processLog.setMessage("事件类型=PAYMENT_EXPIRED_CLOSED");
        processLog.setSource("payment-core");
        paymentMapper.insertPaymentProcessLog(processLog);
        return true;
    }
}
