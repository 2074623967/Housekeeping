package com.abc123.hsp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 本地告警通知器抽象基类，统一生成供应商投递回执。
 */
abstract class AbstractLocalPaymentIssueAlertNotifier {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 为外部 HTTP/Webhook 通知器配置统一的连接与读取超时，避免网关长时间阻塞任务线程。
     */
    protected static RestTemplate buildRestTemplate(int timeoutMs) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return new RestTemplate(requestFactory);
    }

    /**
     * 构造外部网关调用请求体，统一设置 JSON 请求头，并按需附加认证头。
     */
    protected HttpEntity<Map<String, Object>> buildWebhookRequestEntity(Map<String, Object> payload,
                                                                        String authHeaderName,
                                                                        String authHeaderValue,
                                                                        String signatureHeaderName,
                                                                        String signatureSecret) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(authHeaderName) && StringUtils.hasText(authHeaderValue)) {
            headers.set(authHeaderName.trim(), authHeaderValue.trim());
        }
        if (StringUtils.hasText(signatureHeaderName) && StringUtils.hasText(signatureSecret)) {
            headers.set(signatureHeaderName.trim(), signPayload(payload, signatureSecret));
        }
        return new HttpEntity<Map<String, Object>>(payload, headers);
    }

    private String signPayload(Map<String, Object> payload, String signatureSecret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(OBJECT_MAPPER.writeValueAsBytes(payload));
            return Base64.getEncoder().encodeToString(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("网关签名计算失败：" + exception.getMessage(), exception);
        }
    }

    /**
     * 构造本地模拟告警供应商投递结果。
     */
    protected PaymentIssueAlertDeliveryResultDTO buildLocalDeliveryResult(PaymentIssueAlertDispatchItemDTO item,
                                                                         String providerStatus,
                                                                         String channelLabel) {
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptNo(buildReceiptNo(channelLabel, item.getIssueNo()));
        result.setProviderDeliveryStatus(providerStatus);
        result.setProviderDeliveryMessage(buildDeliveryMessage(item, channelLabel));
        result.setRenderedContentSnapshot(StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : buildRenderedContent(item, channelLabel));
        return result;
    }

    /**
     * 构造外部 HTTP/Webhook 网关投递结果，并按可选的业务成功码规则校验响应体。
     */
    protected PaymentIssueAlertDeliveryResultDTO buildWebhookDeliveryResult(PaymentIssueAlertDispatchItemDTO item,
                                                                           String channelLabel,
                                                                           String receiptPrefix,
                                                                           String responseBody,
                                                                           int statusCode,
                                                                           int timeoutMs,
                                                                           String successJsonPointer,
                                                                           String successExpectedValue,
                                                                           String receiptNoJsonPointer) {
        validateWebhookBusinessResponse(channelLabel, responseBody, successJsonPointer, successExpectedValue);
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptNo(resolveWebhookReceiptNo(item, receiptPrefix, responseBody, receiptNoJsonPointer));
        result.setProviderDeliveryStatus("ACCEPTED");
        result.setProviderDeliveryMessage(channelLabel + " HTTP 网关已受理，HTTP=" + statusCode + "，timeout=" + timeoutMs + "ms");
        result.setRenderedContentSnapshot(StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : item.getAlertContent());
        return result;
    }

    private void validateWebhookBusinessResponse(String channelLabel,
                                                 String responseBody,
                                                 String successJsonPointer,
                                                 String successExpectedValue) {
        if (!StringUtils.hasText(successJsonPointer) || !StringUtils.hasText(successExpectedValue)) {
            return;
        }
        JsonNode node = readJsonNode(channelLabel, responseBody);
        JsonNode successNode = node.at(successJsonPointer);
        String actualValue = successNode.isMissingNode() || successNode.isNull() ? "" : successNode.asText();
        if (!successExpectedValue.equals(actualValue)) {
            throw new IllegalStateException(channelLabel + " 网关业务响应未通过，期望="
                    + successExpectedValue + "，实际=" + actualValue);
        }
    }

    private String resolveWebhookReceiptNo(PaymentIssueAlertDispatchItemDTO item,
                                           String receiptPrefix,
                                           String responseBody,
                                           String receiptNoJsonPointer) {
        if (!StringUtils.hasText(receiptNoJsonPointer)) {
            return buildWebhookReceiptNo(receiptPrefix, item.getAlertNo());
        }
        JsonNode node = readJsonNode(receiptPrefix, responseBody);
        JsonNode receiptNode = node.at(receiptNoJsonPointer);
        if (receiptNode.isMissingNode() || receiptNode.isNull() || !StringUtils.hasText(receiptNode.asText())) {
            return buildWebhookReceiptNo(receiptPrefix, item.getAlertNo());
        }
        return receiptNode.asText().trim();
    }

    private JsonNode readJsonNode(String channelLabel, String responseBody) {
        if (!StringUtils.hasText(responseBody)) {
            throw new IllegalStateException(channelLabel + " 网关响应体为空，无法校验业务结果");
        }
        try {
            return OBJECT_MAPPER.readTree(responseBody);
        } catch (Exception exception) {
            throw new IllegalStateException(channelLabel + " 网关响应体不是合法 JSON：" + exception.getMessage(), exception);
        }
    }

    protected String buildWebhookReceiptNo(String receiptPrefix, String alertNo) {
        String normalizedAlertNo = StringUtils.hasText(alertNo) ? alertNo.replaceAll("[^A-Za-z0-9]", "") : "UNKNOWN";
        if (normalizedAlertNo.length() > 20) {
            normalizedAlertNo = normalizedAlertNo.substring(normalizedAlertNo.length() - 20);
        }
        return receiptPrefix + "-" + normalizedAlertNo;
    }

    private String buildReceiptNo(String channelLabel, String issueNo) {
        String issueSuffix = StringUtils.hasText(issueNo) ? issueNo.replaceAll("[^A-Za-z0-9]", "") : "UNKNOWN";
        if (issueSuffix.length() > 16) {
            issueSuffix = issueSuffix.substring(issueSuffix.length() - 16);
        }
        return channelLabel + "-" + issueSuffix;
    }

    private String buildDeliveryMessage(PaymentIssueAlertDispatchItemDTO item, String channelLabel) {
        StringBuilder builder = new StringBuilder("本地");
        builder.append(channelLabel).append("通知器已受理");
        if (StringUtils.hasText(item.getProviderName())) {
            builder.append("，供应商=").append(item.getProviderName());
        }
        if (StringUtils.hasText(item.getTemplateCode())) {
            builder.append("，模板=").append(item.getTemplateCode());
        }
        return builder.toString();
    }

    private String buildRenderedContent(PaymentIssueAlertDispatchItemDTO item, String channelLabel) {
        StringBuilder builder = new StringBuilder();
        builder.append("[").append(channelLabel).append("告警]");
        if (StringUtils.hasText(item.getSeverity())) {
            builder.append("[").append(item.getSeverity()).append("]");
        }
        if (StringUtils.hasText(item.getIssueType())) {
            builder.append(item.getIssueType()).append(" - ");
        }
        if (StringUtils.hasText(item.getAlertContent())) {
            builder.append(item.getAlertContent());
        }
        if (StringUtils.hasText(item.getScheduleTag())) {
            builder.append("（班次：").append(item.getScheduleTag()).append("）");
        }
        return builder.toString();
    }
}
