package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentOpsConfigSnapshotDTO;
import com.abc123.hsp.dto.PaymentOpsSystemControlDTO;
import com.abc123.hsp.service.PaymentOpsConfigService;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * 运营配置快照 HTTP 联动实现。
 */
@Service
public class PaymentOpsConfigServiceImpl implements PaymentOpsConfigService {

    private static final int RESPONSE_CODE_MAX_LENGTH = 32;
    private static final int SCENE_CODE_MAX_LENGTH = 64;
    private static final int DEFAULT_PAY_METHOD_MAX_LENGTH = 64;
    private static final int CHANNEL_PROFILE_CODE_MAX_LENGTH = 64;
    private static final int ROUTE_MATCH_POLICY_MAX_LENGTH = 128;
    private static final int CONTROL_CODE_MAX_LENGTH = 64;
    private static final int CONTROL_NAME_MAX_LENGTH = 128;
    private static final int CONTROL_SCOPE_MAX_LENGTH = 128;
    private static final int CONTROL_VALUE_MAX_LENGTH = 128;
    private static final int RISK_LEVEL_MAX_LENGTH = 64;
    private static final int STATUS_MAX_LENGTH = 32;
    private static final int STATUS_TYPE_MAX_LENGTH = 32;
    private static final int UPDATED_AT_MAX_LENGTH = 32;
    private static final int SYSTEM_CONTROL_MAX_COUNT = 1000;
    private static final List<String> STATUS_TYPES = Arrays.asList(
            "info", "warn", "danger", "success");

    private final boolean enabled;
    private final String baseUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentOpsConfigServiceImpl(
            @Value("${payment.ops-config.enabled:false}") boolean enabled,
            @Value("${payment.ops-config.snapshot-url:}") String baseUrl,
            @Value("${payment.ops-config.timeout-ms:2000}") int timeoutMs) {
        this(enabled, baseUrl, AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    PaymentOpsConfigServiceImpl(boolean enabled, String baseUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.baseUrl = baseUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentOpsConfigSnapshotDTO loadEffectiveSnapshot(String businessCode, String payType, String terminalType) {
        if (!enabled || !StringUtils.hasText(baseUrl)) {
            return null;
        }
        String normalizedBusinessCode = normalizeRequired(businessCode, SCENE_CODE_MAX_LENGTH);
        String normalizedPayType = normalizeRequired(payType, SCENE_CODE_MAX_LENGTH);
        String normalizedTerminalType = normalizeRequired(terminalType, SCENE_CODE_MAX_LENGTH);
        if (normalizedBusinessCode == null || normalizedPayType == null || normalizedTerminalType == null) {
            return null;
        }
        try {
            String requestUrl = UriComponentsBuilder.fromHttpUrl(baseUrl.trim())
                    .queryParam("businessCode", normalizedBusinessCode)
                    .queryParam("payType", normalizedPayType)
                    .queryParam("terminalType", normalizedTerminalType)
                    .build()
                    .toUriString();
            OpsConfigApiResponse response = restTemplate.getForObject(requestUrl, OpsConfigApiResponse.class);
            return validateAndNormalize(
                    response,
                    normalizedBusinessCode,
                    normalizedPayType,
                    normalizedTerminalType);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private PaymentOpsConfigSnapshotDTO validateAndNormalize(
            OpsConfigApiResponse response,
            String businessCode,
            String payType,
            String terminalType) {
        if (response == null || response.getData() == null
                || !"0".equals(normalizeRequired(response.getCode(), RESPONSE_CODE_MAX_LENGTH))) {
            return null;
        }
        PaymentOpsConfigSnapshotDTO snapshot = response.getData();
        if (snapshot.getSnapshotVersion() == null || snapshot.getSnapshotVersion().longValue() < 0L) {
            return null;
        }
        String snapshotBusinessCode = normalizeRequired(snapshot.getBusinessCode(), SCENE_CODE_MAX_LENGTH);
        String snapshotPayType = normalizeRequired(snapshot.getPayType(), SCENE_CODE_MAX_LENGTH);
        String snapshotTerminalType = normalizeRequired(snapshot.getTerminalType(), SCENE_CODE_MAX_LENGTH);
        if (!businessCode.equalsIgnoreCase(snapshotBusinessCode)
                || !payType.equalsIgnoreCase(snapshotPayType)
                || !terminalType.equalsIgnoreCase(snapshotTerminalType)) {
            return null;
        }
        String defaultPayMethod = normalizeRequired(
                snapshot.getDefaultPayMethod(), DEFAULT_PAY_METHOD_MAX_LENGTH);
        String primaryChannelProfileCode = normalizeRequired(
                snapshot.getPrimaryChannelProfileCode(), CHANNEL_PROFILE_CODE_MAX_LENGTH);
        String backupChannelProfileCode = normalizeRequired(
                snapshot.getBackupChannelProfileCode(), CHANNEL_PROFILE_CODE_MAX_LENGTH);
        String routeMatchPolicy = normalizeRequired(
                snapshot.getRouteMatchPolicy(), ROUTE_MATCH_POLICY_MAX_LENGTH);
        if (defaultPayMethod == null || primaryChannelProfileCode == null
                || backupChannelProfileCode == null || routeMatchPolicy == null) {
            return null;
        }
        if (!normalizeSystemControls(snapshot.getEnabledSystemControls())) {
            return null;
        }
        snapshot.setBusinessCode(snapshotBusinessCode);
        snapshot.setPayType(snapshotPayType);
        snapshot.setTerminalType(snapshotTerminalType);
        snapshot.setDefaultPayMethod(defaultPayMethod);
        snapshot.setPrimaryChannelProfileCode(primaryChannelProfileCode);
        snapshot.setBackupChannelProfileCode(backupChannelProfileCode);
        snapshot.setRouteMatchPolicy(routeMatchPolicy);
        return snapshot;
    }

    private boolean normalizeSystemControls(List<PaymentOpsSystemControlDTO> controls) {
        if (controls == null || controls.size() > SYSTEM_CONTROL_MAX_COUNT) {
            return false;
        }
        Set<String> controlCodes = new HashSet<String>();
        for (PaymentOpsSystemControlDTO control : controls) {
            if (control == null) {
                return false;
            }
            String controlCode = normalizeRequired(control.getControlCode(), CONTROL_CODE_MAX_LENGTH);
            String rawStatus = normalizeRequired(control.getStatus(), STATUS_MAX_LENGTH);
            String rawStatusType = normalizeRequired(control.getStatusType(), STATUS_TYPE_MAX_LENGTH);
            if (controlCode == null || rawStatus == null || rawStatusType == null) {
                return false;
            }
            if (!controlCodes.add(controlCode.toUpperCase(Locale.ROOT))) {
                return false;
            }
            String status = rawStatus.toUpperCase(Locale.ROOT);
            String statusType = rawStatusType.toLowerCase(Locale.ROOT);
            if (!"ENABLED".equals(status) || !STATUS_TYPES.contains(statusType)) {
                return false;
            }
            String controlName = normalizeRequired(control.getControlName(), CONTROL_NAME_MAX_LENGTH);
            String controlScope = normalizeRequired(control.getControlScope(), CONTROL_SCOPE_MAX_LENGTH);
            String controlValue = normalizeRequired(control.getControlValue(), CONTROL_VALUE_MAX_LENGTH);
            String riskLevel = normalizeRequired(control.getRiskLevel(), RISK_LEVEL_MAX_LENGTH);
            String updatedAt = normalizeRequired(control.getUpdatedAt(), UPDATED_AT_MAX_LENGTH);
            if (controlName == null || controlScope == null || controlValue == null
                    || riskLevel == null || updatedAt == null) {
                return false;
            }
            control.setControlCode(controlCode);
            control.setControlName(controlName);
            control.setControlScope(controlScope);
            control.setControlValue(controlValue);
            control.setRiskLevel(riskLevel);
            control.setStatus(status);
            control.setStatusType(statusType);
            control.setUpdatedAt(updatedAt);
        }
        return true;
    }

    private String normalizeRequired(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                return null;
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : null;
    }

    /**
     * 对齐运营配置系统统一响应结构。
     */
    @Data
    public static class OpsConfigApiResponse {

        /** 返回码。 */
        private String code;
        /** 返回消息。 */
        private String message;
        /** 快照数据。 */
        private PaymentOpsConfigSnapshotDTO data;
    }
}
