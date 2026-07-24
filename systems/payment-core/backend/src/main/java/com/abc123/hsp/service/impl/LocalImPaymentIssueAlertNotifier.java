package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import org.springframework.stereotype.Component;

/**
 * 本地 IM 告警通知器。
 */
@Component
public class LocalImPaymentIssueAlertNotifier extends AbstractLocalPaymentIssueAlertNotifier
        implements PaymentIssueAlertNotifier {

    @Override
    public String channelCode() {
        return "IM";
    }

    @Override
    public PaymentIssueAlertDeliveryResultDTO send(PaymentIssueAlertDispatchItemDTO item) {
        // 本地骨架阶段返回标准化供应商回执，后续可替换为企业微信、钉钉或 Slack 适配器。
        return buildLocalDeliveryResult(item, "ACCEPTED", "IM");
    }
}
