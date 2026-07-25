package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

/**
 * 邮件告警通知器，支持外部 HTTP 网关优先、本地回执兜底。
 */
@Component
public class LocalEmailPaymentIssueAlertNotifier extends AbstractLocalPaymentIssueAlertNotifier
        implements PaymentIssueAlertNotifier {

    private final RestTemplate restTemplate;
    private String webhookUrl;
    private final int timeoutMs;

    public LocalEmailPaymentIssueAlertNotifier(@Value("${payment.issue-alert.email.webhook-url:}") String webhookUrl,
                                               @Value("${payment.issue-alert.email.timeout-ms:3000}") int timeoutMs) {
        this(webhookUrl, timeoutMs, null);
    }

    LocalEmailPaymentIssueAlertNotifier(RestTemplate restTemplate, String webhookUrl) {
        this(restTemplate, webhookUrl, 3000);
    }

    LocalEmailPaymentIssueAlertNotifier(String webhookUrl, int timeoutMs, RestTemplate restTemplate) {
        this(restTemplate == null ? buildRestTemplate(timeoutMs) : restTemplate, webhookUrl, timeoutMs);
    }

    LocalEmailPaymentIssueAlertNotifier(RestTemplate restTemplate, String webhookUrl, int timeoutMs) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
        this.timeoutMs = timeoutMs;
    }

    @Override
    public String channelCode() {
        return "EMAIL";
    }

    @Override
    public PaymentIssueAlertDeliveryResultDTO send(PaymentIssueAlertDispatchItemDTO item) {
        if (StringUtils.hasText(webhookUrl)) {
            return sendWebhookAlert(item);
        }
        return buildLocalDeliveryResult(item, "ACCEPTED", "EMAIL");
    }

    private PaymentIssueAlertDeliveryResultDTO sendWebhookAlert(PaymentIssueAlertDispatchItemDTO item) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(webhookUrl, buildWebhookPayload(item), String.class);
            PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
            result.setProviderReceiptNo(buildWebhookReceiptNo(item));
            result.setProviderDeliveryStatus("ACCEPTED");
            result.setProviderDeliveryMessage("EMAIL HTTP 网关已受理，HTTP=" + response.getStatusCodeValue() + "，timeout=" + timeoutMs + "ms");
            result.setRenderedContentSnapshot(StringUtils.hasText(item.getRenderedAlertContent())
                    ? item.getRenderedAlertContent()
                    : item.getAlertContent());
            return result;
        } catch (RestClientException exception) {
            throw new IllegalStateException("EMAIL HTTP 网关通知失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildWebhookPayload(PaymentIssueAlertDispatchItemDTO item) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("alertNo", item.getAlertNo());
        payload.put("issueNo", item.getIssueNo());
        payload.put("paymentOrderId", item.getPaymentOrderId());
        payload.put("severity", item.getSeverity());
        payload.put("issueType", item.getIssueType());
        payload.put("responsibilityGroup", item.getResponsibilityGroup());
        payload.put("receiver", item.getReceiver());
        payload.put("providerCode", item.getProviderCode());
        payload.put("templateCode", item.getTemplateCode());
        payload.put("content", StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : item.getAlertContent());
        return payload;
    }

    private String buildWebhookReceiptNo(PaymentIssueAlertDispatchItemDTO item) {
        String alertNo = StringUtils.hasText(item.getAlertNo()) ? item.getAlertNo().replaceAll("[^A-Za-z0-9]", "") : "UNKNOWN";
        if (alertNo.length() > 20) {
            alertNo = alertNo.substring(alertNo.length() - 20);
        }
        return "EMAIL-HTTP-" + alertNo;
    }
}
