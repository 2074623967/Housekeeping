package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentExpiryCloseResultDTO;

/**
 * 支付超时关单任务服务。
 */
public interface PaymentExpiryTaskService {

    /**
     * 关闭超时未收口支付单，返回批次处理结果。
     */
    PaymentExpiryCloseResultDTO closeExpiredPayments();
}
