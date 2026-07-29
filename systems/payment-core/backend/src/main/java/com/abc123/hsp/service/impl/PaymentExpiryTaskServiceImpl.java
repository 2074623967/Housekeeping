package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
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
            paymentMapper.insertEvent(
                    "EVT" + System.currentTimeMillis() + payment.getPaymentOrderId().substring(Math.max(0, payment.getPaymentOrderId().length() - 4)),
                    "PAYMENT_EXPIRED_CLOSED",
                    payment.getPaymentOrderId(),
                    payment.getOrderNo(),
                    "{\"source\":\"scheduler\",\"reason\":\"PREPAY_EXPIRED\"}"
            );
        }
        return expiredPayments.size();
    }
}
