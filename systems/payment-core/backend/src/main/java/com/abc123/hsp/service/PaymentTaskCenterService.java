package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentTaskActionResultDTO;
import com.abc123.hsp.dto.PaymentTaskCenterOverviewDTO;

/**
 * 支付任务中心服务。
 */
public interface PaymentTaskCenterService {

    /**
     * 查询支付任务中心总览。
     */
    PaymentTaskCenterOverviewDTO overview();

    /**
     * 手动执行超时关单任务。
     */
    PaymentTaskActionResultDTO runCloseExpiredPayments();

    /**
     * 手动执行失败事件重发。
     */
    PaymentTaskActionResultDTO runRepublishFailedEvents();

    /**
     * 手动执行失败退款重试。
     */
    PaymentTaskActionResultDTO runRetryFailedRefunds();
}
