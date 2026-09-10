package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.service.PaymentRiskControlService;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

/**
 * 风控中心 HTTP 联动实现。
 */
@Service
public class PaymentRiskControlServiceImpl implements PaymentRiskControlService {

    private static final int RESPONSE_CODE_MAX_LENGTH = 32;
    private static final int DECISION_MAX_LENGTH = 32;
    private static final int DECISION_TYPE_MAX_LENGTH = 32;
    private static final int HIT_CODE_MAX_LENGTH = 64;
    private static final int RISK_TAG_MAX_LENGTH = 128;
    private static final int REVIEW_NO_MAX_LENGTH = 64;
    private static final int EVENT_NO_MAX_LENGTH = 64;
    private static final int MESSAGE_MAX_LENGTH = 255;
    private static final List<String> DECISIONS = Arrays.asList(
            "PASS", "REVIEW", "INTERCEPT", "REJECT");
    private static final List<String> DECISION_TYPES = Arrays.asList(
            "info", "warn", "danger", "success");

    private final boolean enabled;
    private final String evaluateUrl;
    private final RestTemplate restTemplate;

    @Autowired
    public PaymentRiskControlServiceImpl(
            @Value("${payment.risk-control.enabled:false}") boolean enabled,
            @Value("${payment.risk-control.evaluate-url:}") String evaluateUrl,
            @Value("${payment.risk-control.timeout-ms:2000}") int timeoutMs) {
        this(enabled, evaluateUrl, AbstractLocalPaymentIssueAlertNotifier.buildRestTemplate(timeoutMs));
    }

    PaymentRiskControlServiceImpl(boolean enabled, String evaluateUrl, RestTemplate restTemplate) {
        this.enabled = enabled;
        this.evaluateUrl = evaluateUrl;
        this.restTemplate = restTemplate;
    }

    @Override
    public PaymentRiskDecisionResultDTO evaluateSubmitRisk(PaymentRiskDecisionRequestDTO request) {
        if (!enabled || !StringUtils.hasText(evaluateUrl)) {
            return passResult("未启用独立风控中心联动，当前按本地放行口径继续处理");
        }
        try {
            RiskApiResponse response = restTemplate.postForObject(
                    evaluateUrl.trim(),
                    request,
                    RiskApiResponse.class);
            return validateAndNormalize(response);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, "风控中心调用失败，请稍后重试或联系资金风控团队");
        }
    }

    private PaymentRiskDecisionResultDTO passResult(String message) {
        PaymentRiskDecisionResultDTO result = new PaymentRiskDecisionResultDTO();
        result.setDecision("PASS");
        result.setDecisionType("success");
        result.setMessage(message);
        return result;
    }

    private PaymentRiskDecisionResultDTO validateAndNormalize(RiskApiResponse response) {
        if (response == null || response.getData() == null) {
            throw invalidResult();
        }
        String responseCode = normalizeRequired(response.getCode(), RESPONSE_CODE_MAX_LENGTH);
        if (!"0".equals(responseCode)) {
            throw invalidResult();
        }
        PaymentRiskDecisionResultDTO result = response.getData();
        String decision = normalizeRequired(result.getDecision(), DECISION_MAX_LENGTH)
                .toUpperCase(Locale.ROOT);
        if (!DECISIONS.contains(decision)) {
            throw invalidResult();
        }
        String decisionType = normalizeRequired(result.getDecisionType(), DECISION_TYPE_MAX_LENGTH)
                .toLowerCase(Locale.ROOT);
        if (!DECISION_TYPES.contains(decisionType)) {
            throw invalidResult();
        }
        result.setDecision(decision);
        result.setDecisionType(decisionType);
        result.setHitCode(normalizeOptional(result.getHitCode(), HIT_CODE_MAX_LENGTH));
        result.setRiskTag(normalizeOptional(result.getRiskTag(), RISK_TAG_MAX_LENGTH));
        result.setReviewNo(normalizeOptional(result.getReviewNo(), REVIEW_NO_MAX_LENGTH));
        result.setEventNo(normalizeOptional(result.getEventNo(), EVENT_NO_MAX_LENGTH));
        result.setMessage(normalizeOptional(result.getMessage(), MESSAGE_MAX_LENGTH));
        return result;
    }

    private String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw invalidResult();
        }
        return normalized;
    }

    private String normalizeOptional(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidResult();
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw invalidResult();
        }
        return normalized;
    }

    private BusinessException invalidResult() {
        return new BusinessException(
                ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE,
                "风控中心返回结果非法，无法确认支付准入状态");
    }

    /**
     * 对齐风控中心统一响应结构。
     */
    public static class RiskApiResponse {

        /** 返回码。 */
        private String code;
        /** 返回消息。 */
        private String message;
        /** 决策结果。 */
        private PaymentRiskDecisionResultDTO data;

        public String getCode() {
            return code;
        }

        public void setCode(String code) {
            this.code = code;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public PaymentRiskDecisionResultDTO getData() {
            return data;
        }

        public void setData(PaymentRiskDecisionResultDTO data) {
            this.data = data;
        }
    }
}
