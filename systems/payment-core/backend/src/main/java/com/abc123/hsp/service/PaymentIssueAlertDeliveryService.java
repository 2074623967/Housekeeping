package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;

/**
 * 支付交易异常告警派发服务。
 */
public interface PaymentIssueAlertDeliveryService {

    /**
     * 手动派发待处理的异常告警。
     */
    PaymentTaskActionResultDTO dispatchPendingAlerts();

    /**
     * 自动派发待处理的异常告警。
     */
    PaymentTaskActionResultDTO autoDispatchPendingAlerts();
}
