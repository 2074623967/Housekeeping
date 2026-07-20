package com.abc123.hsp.service;

/**
 * 支付超时关单任务服务。
 */
public interface PaymentExpiryTaskService {

    /**
     * 关闭超时未收口支付单，返回成功关闭数量。
     */
    int closeExpiredPayments();
}
