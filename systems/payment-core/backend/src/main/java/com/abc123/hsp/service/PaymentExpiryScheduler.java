package com.abc123.hsp.service;

import com.abc123.hsp.dto.ExpiredPaymentDTO;
import com.abc123.hsp.mapper.PaymentMapper;
import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 支付超时补偿任务。
 *
 * <p>收银台失效后不能依赖用户再次访问页面才能关闭支付单，
 * 因此由后台任务主动收口待支付和支付中的超时单。</p>
 */
@Component
public class PaymentExpiryScheduler {

    private final PaymentMapper paymentMapper;

    public PaymentExpiryScheduler(PaymentMapper paymentMapper) {
        this.paymentMapper = paymentMapper;
    }

    /**
     * 定期关闭已超过收银台失效时间的支付单。
     */
    @Scheduled(fixedDelayString = "${payment.expiry-close.fixed-delay-ms:60000}")
    @Transactional
    public void closeExpiredPayments() {
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
                    "EVT" + System.currentTimeMillis(),
                    "PAYMENT_EXPIRED_CLOSED",
                    payment.getPaymentOrderId(),
                    payment.getOrderNo(),
                    "{\"source\":\"scheduler\",\"reason\":\"PREPAY_EXPIRED\"}"
            );
        }
    }
}
