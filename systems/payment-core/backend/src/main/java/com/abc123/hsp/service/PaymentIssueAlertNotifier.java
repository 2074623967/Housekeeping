package com.abc123.hsp.service;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;

/**
 * 支付交易异常告警通知器。
 */
public interface PaymentIssueAlertNotifier {

    /**
     * 当前通知器对应的通道编码。
     */
    String channelCode();

    /**
     * 发送告警。
     */
    PaymentIssueAlertDeliveryResultDTO send(PaymentIssueAlertDispatchItemDTO item);
}
