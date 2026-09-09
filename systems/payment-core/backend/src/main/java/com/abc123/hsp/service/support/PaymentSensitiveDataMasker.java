package com.abc123.hsp.service.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/** Masks sensitive payment facts at outward-facing API and export boundaries. */
@Component
public class PaymentSensitiveDataMasker {

    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");
    private static final Set<String> PHONE_FIELDS = fields("payerPhone", "mobile", "phone", "tel");
    private static final Set<String> CARD_FIELDS = fields("bankCardNo", "cardNo");
    private static final Set<String> NAME_FIELDS = fields("customerName", "name");
    private static final Set<String> IDENTIFIER_FIELDS = fields("clientDeviceId");
    private static final Set<String> PAYLOAD_FIELDS = fields("requestPayload", "responsePayload");

    private final ObjectMapper objectMapper;

    public PaymentSensitiveDataMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String maskClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp) || "UNKNOWN".equalsIgnoreCase(clientIp.trim())) {
            return clientIp;
        }
        String normalized = clientIp.trim();
        if (IP_ADDRESS_PATTERN.matcher(normalized).matches()) {
            String[] parts = normalized.split("\\.");
            return parts[0] + "." + parts[1] + ".*.*";
        }
        if (normalized.contains(":")) {
            String[] segments = normalized.split(":");
            return segments.length == 0 ? normalized : segments[0] + ":****";
        }
        return maskIdentifier(normalized);
    }

    public String maskIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return idempotencyKey;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() <= 10) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "****";
        }
        return normalized.substring(0, 6) + "****" + normalized.substring(normalized.length() - 4);
    }

    public String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String normalized = name.trim();
        return normalized.length() <= 1 ? "*" : normalized.substring(0, 1) + "*";
    }

    public String maskUserReference(String reference) {
        if (!StringUtils.hasText(reference)) {
            return reference;
        }
        String normalized = reference.trim();
        int separatorIndex = normalized.indexOf('-');
        if (separatorIndex > 0) {
            return normalized.substring(0, separatorIndex + 1) + "****";
        }
        return "****";
    }

    public String maskPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return payload;
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            if (root == null || (!root.isObject() && !root.isArray())) {
                return "[REDACTED]";
            }
            maskNode(root);
            return objectMapper.writeValueAsString(root);
        } catch (JsonProcessingException exception) {
            return "[REDACTED]";
        }
    }

    private void maskNode(JsonNode node) {
        if (node == null) {
            return;
        }
        if (node.isArray()) {
            ArrayNode arrayNode = (ArrayNode) node;
            for (JsonNode child : arrayNode) {
                maskNode(child);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        ObjectNode objectNode = (ObjectNode) node;
        Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            String normalizedName = field.getKey().toLowerCase(Locale.ROOT);
            JsonNode value = field.getValue();
            if ("clientip".equals(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskClientIp));
            } else if ("idempotencykey".equals(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskIdempotencyKey));
            } else if (PHONE_FIELDS.contains(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskPhone));
            } else if (CARD_FIELDS.contains(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskBankCardNo));
            } else if (NAME_FIELDS.contains(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskName));
            } else if (IDENTIFIER_FIELDS.contains(normalizedName)) {
                objectNode.set(field.getKey(), maskedText(value, this::maskIdentifier));
            } else if (PAYLOAD_FIELDS.contains(normalizedName)) {
                objectNode.set(field.getKey(), TextNode.valueOf("[REDACTED]"));
            } else {
                maskNode(value);
            }
        }
    }

    private JsonNode maskedText(JsonNode value, java.util.function.Function<String, String> masker) {
        if (value == null || value.isNull()) {
            return value;
        }
        return TextNode.valueOf(value.isTextual() ? masker.apply(value.textValue()) : "****");
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String normalized = phone.trim();
        if (normalized.length() <= 7) {
            return normalized.substring(0, Math.min(3, normalized.length())) + "****";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String maskBankCardNo(String bankCardNo) {
        if (!StringUtils.hasText(bankCardNo)) {
            return bankCardNo;
        }
        String normalized = bankCardNo.trim();
        if (normalized.length() <= 10) {
            return normalized.substring(0, Math.min(4, normalized.length())) + "****";
        }
        return normalized.substring(0, 6) + "******" + normalized.substring(normalized.length() - 4);
    }

    private String maskIdentifier(String identifier) {
        if (!StringUtils.hasText(identifier)) {
            return identifier;
        }
        String normalized = identifier.trim();
        if (normalized.length() <= 8) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "****";
        }
        return normalized.substring(0, 4) + "****" + normalized.substring(normalized.length() - 4);
    }

    private static Set<String> fields(String... fieldNames) {
        Set<String> fields = new HashSet<String>();
        Arrays.stream(fieldNames)
                .map(fieldName -> fieldName.toLowerCase(Locale.ROOT))
                .forEach(fields::add);
        return fields;
    }
}
