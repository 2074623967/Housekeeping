package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import org.springframework.stereotype.Component;

/**
 * 本地邮件告警通知器。
 */
@Component
public class LocalEmailPaymentIssueAlertNotifier extends AbstractLocalPaymentIssueAlertNotifier
        implements PaymentIssueAlertNotifier {

    @Override
    public String channelCode() {
        return "EMAIL";
    }

    @Override
    public PaymentIssueAlertDeliveryResultDTO send(PaymentIssueAlertDispatchItemDTO item) {
        // 本地骨架阶段返回标准化供应商回执，后续可替换为真实邮件网关。
        return buildLocalDeliveryResult(item, "ACCEPTED", "EMAIL");
    }
}
