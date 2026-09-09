package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.entity.PaymentProcessLogEntity;
import com.abc123.hsp.mapper.PaymentMapper;
import com.abc123.hsp.service.PaymentExpiryTaskService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付超时关单任务服务默认实现。
 */
@Service
public class PaymentExpiryTaskServiceImpl implements PaymentExpiryTaskService {

    private final PaymentMapper paymentMapper;

    public PaymentExpiryTaskServiceImpl(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    @Override
    @Transactional
    public int closeExpiredPayments() {
        List<ExpiredPaymentDTO> expiredPayments = paymentMapper.findExpiredPayments();
        for (ExpiredPaymentDTO payment : expiredPayments) {
            paymentMapper.updatePaymentStatus(
                    payment.getPaymentOrderId(),
                    "CLOSED",
                    "danger",
                    payment.getChannelTransactionNo()
            );
            paymentMapper.updatePaymentAttemptStatusByPaymentOrderId(
                    payment.getPaymentOrderId(),
                    "已关闭",
                    "danger"
            );
            paymentMapper.updatePrepayStatusByPaymentOrderId(
                    payment.getPaymentOrderId(),
                    "已失效",
                    "danger"
            );
            String eventNo = "EVT" + System.currentTimeMillis()
                    + payment.getPaymentOrderId().substring(Math.max(0, payment.getPaymentOrderId().length() - 4));
            String eventPayload = "{\"source\":\"scheduler\",\"reason\":\"PREPAY_EXPIRED\"}";
            paymentMapper.insertEvent(
                    eventNo,
                    "PAYMENT_EXPIRED_CLOSED",
                    payment.getPaymentOrderId(),
                    payment.getOrderNo(),
                    eventPayload);
            PaymentProcessLogEntity processLog = new PaymentProcessLogEntity();
            processLog.setLogNo(eventNo);
            processLog.setPaymentOrderId(payment.getPaymentOrderId());
            processLog.setOrderNo(payment.getOrderNo());
            processLog.setSourceType("PAYMENT_EVENT");
            processLog.setSourceNo(eventNo);
            processLog.setProcessStage("业务事件");
            processLog.setLogLevel("INFO");
            processLog.setLogLevelType("info");
            processLog.setMessage("事件类型=PAYMENT_EXPIRED_CLOSED；事件载荷=" + eventPayload);
            processLog.setSource("payment-core");
            paymentMapper.insertPaymentProcessLog(processLog);
        }
        return expiredPayments.size();
    }
}
