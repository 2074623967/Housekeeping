package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import org.springframework.stereotype.Component;

/**
 * 本地邮件告警通知器。
 */
@Component
public class LocalEmailPaymentIssueAlertNotifier implements PaymentIssueAlertNotifier {

    @Override
    public String channelCode() {
        return "EMAIL";
    }

    @Override
    public void send(PaymentIssueAlertDispatchItemDTO item) {
        // 本地骨架阶段仅保留统一调用入口，后续可替换为真实邮件网关。
    }
}
