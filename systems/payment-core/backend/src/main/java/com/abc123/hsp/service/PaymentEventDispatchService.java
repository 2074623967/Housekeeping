package com.abc123.hsp.service;

/**
 * 支付事件下游投递服务。
 */
public interface PaymentEventDispatchService {

    /**
     * 发布支付成功事件到下游系统。
     */
    void publishPaymentSuccess(String eventNo, String paymentOrderId);

    /**
     * 将退款申请按稳定退款尝试号提交到原支付渠道。
     *
     * @return 是否已获得可持久化的渠道受理结果
     */
    default boolean publishRefundSubmit(String eventNo, String refundAttemptNo, String operator) {
        return false;
    }

    /**
     * 按事件号重新投递支付事件。
     *
     * @return 是否投递成功
     */
    boolean republish(String eventNo, String operator);
}
