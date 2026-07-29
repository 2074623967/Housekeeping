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
     * 按事件号重新投递支付事件。
     *
     * @return 是否投递成功
     */
    boolean republish(String eventNo);
}
