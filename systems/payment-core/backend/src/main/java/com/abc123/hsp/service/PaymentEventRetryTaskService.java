package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentEventRetryResultDTO;

/**
 * 支付事件批次重发服务。
 */
public interface PaymentEventRetryTaskService {

    /**
     * 重发失败事件；自动模式只处理已到重试时间的事件。
     */
    PaymentEventRetryResultDTO retryFailedEvents(boolean automatic);
}
