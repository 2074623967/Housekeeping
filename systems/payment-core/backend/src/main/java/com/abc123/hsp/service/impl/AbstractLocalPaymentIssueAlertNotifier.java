package com.abc123.hsp.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.abc123.hsp.dto.PaymentIssueAlertDeliveryResultDTO;
import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
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
                                                                        String signatureSecret,
                                                                        String signatureAlgorithm,
                                                                        String timestampHeaderName,
                                                                        String nonceHeaderName) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (StringUtils.hasText(authHeaderName) && StringUtils.hasText(authHeaderValue)) {
            headers.set(authHeaderName.trim(), authHeaderValue.trim());
        }
        if (StringUtils.hasText(signatureHeaderName) && StringUtils.hasText(signatureSecret)) {
            headers.set(signatureHeaderName.trim(), signPayload(payload, signatureSecret, signatureAlgorithm));
        }
        if (StringUtils.hasText(timestampHeaderName)) {
            headers.set(timestampHeaderName.trim(), String.valueOf(System.currentTimeMillis()));
        }
        if (StringUtils.hasText(nonceHeaderName)) {
            headers.set(nonceHeaderName.trim(), UUID.randomUUID().toString().replace("-", ""));
        }
        return new HttpEntity<Map<String, Object>>(payload, headers);
    }

    private String signPayload(Map<String, Object> payload, String signatureSecret, String signatureAlgorithm) {
        try {
            SignatureAlgorithmConfig config = resolveSignatureAlgorithm(signatureAlgorithm);
            byte[] payloadBytes = OBJECT_MAPPER.writeValueAsBytes(payload);
            if (config.isMacAlgorithm()) {
                Mac mac = Mac.getInstance(config.getJcaName());
                mac.init(new SecretKeySpec(signatureSecret.getBytes(StandardCharsets.UTF_8), config.getJcaName()));
                return Base64.getEncoder().encodeToString(mac.doFinal(payloadBytes));
            }
            Signature signature = Signature.getInstance(config.getJcaName());
            signature.initSign(buildPrivateKey(signatureSecret));
            signature.update(payloadBytes);
            return Base64.getEncoder().encodeToString(signature.sign());
        } catch (Exception exception) {
            throw new IllegalStateException("网关签名计算失败：" + exception.getMessage(), exception);
        }
    }

    private SignatureAlgorithmConfig resolveSignatureAlgorithm(String signatureAlgorithm) {
        if (!StringUtils.hasText(signatureAlgorithm)) {
            return new SignatureAlgorithmConfig("HmacSHA256", true);
        }
        String normalizedAlgorithm = signatureAlgorithm.trim().toUpperCase().replace('-', '_');
        if ("HMAC_SHA256".equals(normalizedAlgorithm)) {
            return new SignatureAlgorithmConfig("HmacSHA256", true);
        }
        if ("HMAC_SHA1".equals(normalizedAlgorithm)) {
            return new SignatureAlgorithmConfig("HmacSHA1", true);
        }
        if ("HMAC_MD5".equals(normalizedAlgorithm)) {
            return new SignatureAlgorithmConfig("HmacMD5", true);
        }
        if ("RSA2".equals(normalizedAlgorithm) || "SHA256WITHRSA".equals(normalizedAlgorithm)) {
            return new SignatureAlgorithmConfig("SHA256withRSA", false);
        }
        throw new IllegalArgumentException("不支持的网关签名算法：" + signatureAlgorithm);
    }

    private PrivateKey buildPrivateKey(String privateKeyContent) throws Exception {
        if (!StringUtils.hasText(privateKeyContent)) {
            throw new IllegalArgumentException("RSA 签名私钥不能为空");
        }
        String normalizedKey = privateKeyContent
                .replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN RSA PRIVATE KEY-----", "")
                .replace("-----END RSA PRIVATE KEY-----", "")
                .replaceAll("\\s+", "");
        byte[] keyBytes = Base64.getDecoder().decode(normalizedKey);
        PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);
        return KeyFactory.getInstance("RSA").generatePrivate(keySpec);
    }

    private static final class SignatureAlgorithmConfig {

        private final String jcaName;
        private final boolean macAlgorithm;

        private SignatureAlgorithmConfig(String jcaName, boolean macAlgorithm) {
            this.jcaName = jcaName;
            this.macAlgorithm = macAlgorithm;
        }

        private String getJcaName() {
            return jcaName;
        }

        private boolean isMacAlgorithm() {
            return macAlgorithm;
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
                                                                           String receiptNoJsonPointer,
                                                                           String deliveryStatusJsonPointer,
                                                                           String deliveredStatusValues,
                                                                           String acceptedStatusValues,
                                                                           String failedStatusValues,
                                                                           String failureCodeJsonPointer) {
        validateWebhookBusinessResponse(channelLabel, responseBody, successJsonPointer, successExpectedValue);
        String rawDeliveryStatus = resolveWebhookPointerText(channelLabel, responseBody, deliveryStatusJsonPointer);
        String normalizedDeliveryStatus = resolveNormalizedDeliveryStatus(rawDeliveryStatus,
                deliveredStatusValues,
                acceptedStatusValues,
                failedStatusValues);
        String failureCode = resolveWebhookPointerText(channelLabel, responseBody, failureCodeJsonPointer);
        PaymentIssueAlertDeliveryResultDTO result = new PaymentIssueAlertDeliveryResultDTO();
        result.setProviderReceiptNo(resolveWebhookReceiptNo(item, receiptPrefix, responseBody, receiptNoJsonPointer));
        result.setProviderDeliveryStatus(normalizedDeliveryStatus);
        result.setProviderDeliveryMessage(buildWebhookDeliveryMessage(
                channelLabel,
                statusCode,
                timeoutMs,
                rawDeliveryStatus,
                failureCode,
                normalizedDeliveryStatus
        ));
        result.setRenderedContentSnapshot(StringUtils.hasText(item.getRenderedAlertContent())
                ? item.getRenderedAlertContent()
                : item.getAlertContent());
        return result;
    }

    private String buildWebhookDeliveryMessage(String channelLabel,
                                              int statusCode,
                                              int timeoutMs,
                                              String rawDeliveryStatus,
                                              String failureCode,
                                              String normalizedDeliveryStatus) {
        StringBuilder builder = new StringBuilder(channelLabel)
                .append(" HTTP 网关已响应，HTTP=").append(statusCode)
                .append("，timeout=").append(timeoutMs).append("ms")
                .append("，normalizedStatus=").append(normalizedDeliveryStatus);
        if (StringUtils.hasText(rawDeliveryStatus)) {
            builder.append("，providerStatus=").append(rawDeliveryStatus);
        }
        if (StringUtils.hasText(failureCode)) {
            builder.append("，failureCode=").append(failureCode);
        }
        return builder.toString();
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

    private String resolveWebhookPointerText(String channelLabel,
                                            String responseBody,
                                            String jsonPointer) {
        if (!StringUtils.hasText(jsonPointer)) {
            return "";
        }
        JsonNode node = readJsonNode(channelLabel, responseBody);
        JsonNode valueNode = node.at(jsonPointer);
        if (valueNode.isMissingNode() || valueNode.isNull()) {
            return "";
        }
        return valueNode.asText().trim();
    }

    private String resolveNormalizedDeliveryStatus(String rawDeliveryStatus,
                                                   String deliveredStatusValues,
                                                   String acceptedStatusValues,
                                                   String failedStatusValues) {
        if (!StringUtils.hasText(rawDeliveryStatus)) {
            return "ACCEPTED";
        }
        if (matchesConfiguredStatus(rawDeliveryStatus, deliveredStatusValues)
                || "DELIVERED".equalsIgnoreCase(rawDeliveryStatus)
                || "SENT".equalsIgnoreCase(rawDeliveryStatus)
                || "SUCCESS".equalsIgnoreCase(rawDeliveryStatus)) {
            return "DELIVERED";
        }
        if (matchesConfiguredStatus(rawDeliveryStatus, failedStatusValues)
                || "FAILED".equalsIgnoreCase(rawDeliveryStatus)
                || "FAIL".equalsIgnoreCase(rawDeliveryStatus)
                || "REJECTED".equalsIgnoreCase(rawDeliveryStatus)
                || "ERROR".equalsIgnoreCase(rawDeliveryStatus)) {
            return "FAILED";
        }
        if (matchesConfiguredStatus(rawDeliveryStatus, acceptedStatusValues)
                || "ACCEPTED".equalsIgnoreCase(rawDeliveryStatus)
                || "QUEUED".equalsIgnoreCase(rawDeliveryStatus)
                || "PENDING".equalsIgnoreCase(rawDeliveryStatus)
                || "PROCESSING".equalsIgnoreCase(rawDeliveryStatus)) {
            return "ACCEPTED";
        }
        return "ACCEPTED";
    }

    private boolean matchesConfiguredStatus(String rawDeliveryStatus, String configuredValues) {
        if (!StringUtils.hasText(rawDeliveryStatus) || !StringUtils.hasText(configuredValues)) {
            return false;
        }
        String[] candidates = configuredValues.split("[,|]");
        for (String candidate : candidates) {
            if (rawDeliveryStatus.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
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
