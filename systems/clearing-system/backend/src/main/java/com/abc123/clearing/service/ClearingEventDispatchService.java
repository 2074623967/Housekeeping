package com.abc123.clearing.service;

/**
 * 清分事件下游派发服务。
 */
public interface ClearingEventDispatchService {

    /**
     * 将清分结果继续派发给结算和账务系统。
     *
     * @param paymentOrderId 支付单号
     * @return 是否派发成功
     */
    boolean publishClearingGenerated(String paymentOrderId);
}
