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
 * 短信告警通知器，支持外部 HTTP 网关优先、本地回执兜底。
 */
@Component
public class LocalSmsPaymentIssueAlertNotifier extends AbstractLocalPaymentIssueAlertNotifier
        implements PaymentIssueAlertNotifier {

    private final RestTemplate restTemplate;
    private String webhookUrl;
    private final int timeoutMs;
    private final String successJsonPointer;
    private final String successExpectedValue;
    private final String receiptNoJsonPointer;
    private final String authHeaderName;
    private final String authHeaderValue;
    private final String signatureHeaderName;
    private final String signatureSecret;
    private final String timestampHeaderName;
    private final String nonceHeaderName;

    public LocalSmsPaymentIssueAlertNotifier(@Value("${payment.issue-alert.sms.webhook-url:}") String webhookUrl,
                                             @Value("${payment.issue-alert.sms.timeout-ms:3000}") int timeoutMs,
                                             @Value("${payment.issue-alert.sms.success-code-json-pointer:}") String successJsonPointer,
                                             @Value("${payment.issue-alert.sms.success-code-expected-value:}") String successExpectedValue,
                                             @Value("${payment.issue-alert.sms.receipt-no-json-pointer:}") String receiptNoJsonPointer,
                                             @Value("${payment.issue-alert.sms.auth-header-name:}") String authHeaderName,
                                             @Value("${payment.issue-alert.sms.auth-header-value:}") String authHeaderValue,
                                             @Value("${payment.issue-alert.sms.signature-header-name:}") String signatureHeaderName,
                                             @Value("${payment.issue-alert.sms.signature-secret:}") String signatureSecret,
                                             @Value("${payment.issue-alert.sms.timestamp-header-name:}") String timestampHeaderName,
                                             @Value("${payment.issue-alert.sms.nonce-header-name:}") String nonceHeaderName) {
        this(webhookUrl, timeoutMs, successJsonPointer, successExpectedValue, receiptNoJsonPointer,
                authHeaderName, authHeaderValue, signatureHeaderName, signatureSecret, timestampHeaderName, nonceHeaderName, null);
    }

    LocalSmsPaymentIssueAlertNotifier(RestTemplate restTemplate, String webhookUrl) {
        this(
                webhookUrl,
                3000,
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                "",
                restTemplate
        );
    }

    LocalSmsPaymentIssueAlertNotifier(String webhookUrl,
                                      int timeoutMs,
                                      String successJsonPointer,
                                      String successExpectedValue,
                                      String receiptNoJsonPointer,
                                      String authHeaderName,
                                      String authHeaderValue,
                                      String signatureHeaderName,
                                      String signatureSecret,
                                      String timestampHeaderName,
                                      String nonceHeaderName,
                                      RestTemplate restTemplate) {
        this(restTemplate == null ? buildRestTemplate(timeoutMs) : restTemplate,
                webhookUrl,
                timeoutMs,
                successJsonPointer,
                successExpectedValue,
                receiptNoJsonPointer,
                authHeaderName,
                authHeaderValue,
                signatureHeaderName,
                signatureSecret,
                timestampHeaderName,
                nonceHeaderName);
    }

    LocalSmsPaymentIssueAlertNotifier(RestTemplate restTemplate,
                                      String webhookUrl,
                                      int timeoutMs,
                                      String successJsonPointer,
                                      String successExpectedValue,
                                      String receiptNoJsonPointer,
                                      String authHeaderName,
                                      String authHeaderValue,
                                      String signatureHeaderName,
                                      String signatureSecret,
                                      String timestampHeaderName,
                                      String nonceHeaderName) {
        this.restTemplate = restTemplate;
        this.webhookUrl = webhookUrl;
        this.timeoutMs = timeoutMs;
        this.successJsonPointer = successJsonPointer;
        this.successExpectedValue = successExpectedValue;
        this.receiptNoJsonPointer = receiptNoJsonPointer;
        this.authHeaderName = authHeaderName;
        this.authHeaderValue = authHeaderValue;
        this.signatureHeaderName = signatureHeaderName;
        this.signatureSecret = signatureSecret;
        this.timestampHeaderName = timestampHeaderName;
        this.nonceHeaderName = nonceHeaderName;
    }

    @Override
    public String channelCode() {
        return "SMS";
    }

    @Override
    public PaymentIssueAlertDeliveryResultDTO send(PaymentIssueAlertDispatchItemDTO item) {
        if (StringUtils.hasText(webhookUrl)) {
            return sendWebhookAlert(item);
        }
        return buildLocalDeliveryResult(item, "ACCEPTED", "SMS");
    }

    private PaymentIssueAlertDeliveryResultDTO sendWebhookAlert(PaymentIssueAlertDispatchItemDTO item) {
        try {
            ResponseEntity<String> response = restTemplate.postForEntity(
                    webhookUrl,
                    buildWebhookRequestEntity(buildWebhookPayload(item), authHeaderName, authHeaderValue, signatureHeaderName, signatureSecret, timestampHeaderName, nonceHeaderName),
                    String.class
            );
            return buildWebhookDeliveryResult(
                    item,
                    "SMS",
                    "SMS-HTTP",
                    response.getBody(),
                    response.getStatusCodeValue(),
                    timeoutMs,
                    successJsonPointer,
                    successExpectedValue,
                    receiptNoJsonPointer
            );
        } catch (RestClientException exception) {
            throw new IllegalStateException("SMS HTTP 网关通知失败：" + exception.getMessage(), exception);
        }
    }

    private Map<String, Object> buildWebhookPayload(PaymentIssueAlertDispatchItemDTO item) {
        Map<String, Object> payload = new HashMap<String, Object>();
        payload.put("alertNo", item.getAlertNo());
        payload.put("issueNo", item.getIssueNo());
        payload.put("paymentOrderId", item.getPaymentOrderId());
        payload.put("severity", item.getSeverity());
        payload.put("receiver", item.getReceiver());
        payload.put("providerCode", item.getProviderCode());
        payload.put("templateCode", item.getTemplateCode());
        payload.put("content", StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : item.getAlertContent());
        return payload;
    }

}
