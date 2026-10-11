package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRiskDecisionRequestDTO;
import com.abc123.hsp.dto.PaymentRiskDecisionResultDTO;
import com.abc123.hsp.service.PaymentRiskControlService;
import java.math.BigDecimal;
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

    private static final int BUSINESS_NO_MAX_LENGTH = 64;
    private static final int SOURCE_SYSTEM_MAX_LENGTH = 64;
    private static final int SOURCE_APP_ID_MAX_LENGTH = 64;
    private static final int SCENE_CODE_MAX_LENGTH = 64;
    private static final int PAY_SCENE_MAX_LENGTH = 32;
    private static final int PAYMENT_METHOD_MAX_LENGTH = 32;
    private static final int CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int MERCHANT_NO_MAX_LENGTH = 128;
    private static final int TERMINAL_MAX_LENGTH = 32;
    private static final int CLIENT_IP_MAX_LENGTH = 64;
    private static final int CLIENT_DEVICE_ID_MAX_LENGTH = 128;
    private static final int PAYER_PHONE_MAX_LENGTH = 32;
    private static final int MONEY_MAX_SCALE = 2;
    private static final BigDecimal MAX_MONEY_AMOUNT = new BigDecimal("9999999999999999.99");
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
        if (!enabled) {
            return passResult("未启用独立风控中心联动，当前按本地放行口径继续处理");
        }
        if (!StringUtils.hasText(evaluateUrl)) {
            throw new BusinessException(
                    ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE,
                    "风控中心评估地址未配置，请联系资金风控团队");
        }
        try {
            PaymentRiskDecisionRequestDTO normalizedRequest = validateAndNormalizeRequest(request);
            RiskApiResponse response = restTemplate.postForObject(
                    evaluateUrl.trim(),
                    normalizedRequest,
                    RiskApiResponse.class);
            return validateAndNormalize(response);
        } catch (BusinessException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new BusinessException(ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE, "风控中心调用失败，请稍后重试或联系资金风控团队");
        }
    }

    private PaymentRiskDecisionRequestDTO validateAndNormalizeRequest(PaymentRiskDecisionRequestDTO request) {
        if (request == null) {
            throw invalidRequest();
        }
        PaymentRiskDecisionRequestDTO normalized = new PaymentRiskDecisionRequestDTO();
        normalized.setBusinessNo(normalizeRequiredRequestField(request.getBusinessNo(), BUSINESS_NO_MAX_LENGTH));
        normalized.setSourceSystem(normalizeRequiredRequestField(request.getSourceSystem(), SOURCE_SYSTEM_MAX_LENGTH));
        normalized.setSourceAppId(normalizeRequiredRequestField(request.getSourceAppId(), SOURCE_APP_ID_MAX_LENGTH));
        normalized.setSceneCode(normalizeRequiredRequestField(request.getSceneCode(), SCENE_CODE_MAX_LENGTH));
        normalized.setPayScene(normalizeRequiredRequestField(request.getPayScene(), PAY_SCENE_MAX_LENGTH));
        normalized.setPaymentMethod(normalizeRequiredRequestField(
                request.getPaymentMethod(), PAYMENT_METHOD_MAX_LENGTH));
        normalized.setChannelCode(normalizeRequiredRequestField(request.getChannelCode(), CHANNEL_CODE_MAX_LENGTH));
        normalized.setMerchantNo(normalizeRequiredRequestField(request.getMerchantNo(), MERCHANT_NO_MAX_LENGTH));
        normalized.setTerminal(normalizeRequiredRequestField(request.getTerminal(), TERMINAL_MAX_LENGTH));
        normalized.setClientIp(normalizeRequiredRequestField(request.getClientIp(), CLIENT_IP_MAX_LENGTH));
        normalized.setClientDeviceId(normalizeOptionalRequestField(
                request.getClientDeviceId(), CLIENT_DEVICE_ID_MAX_LENGTH));
        normalized.setPayerPhone(normalizeOptionalRequestField(request.getPayerPhone(), PAYER_PHONE_MAX_LENGTH));
        BigDecimal amount = request.getAmount();
        if (amount == null
                || amount.compareTo(BigDecimal.ZERO) <= 0
                || amount.scale() > MONEY_MAX_SCALE
                || amount.compareTo(MAX_MONEY_AMOUNT) > 0) {
            throw invalidRequest();
        }
        normalized.setAmount(amount);
        return normalized;
    }

    private String normalizeRequiredRequestField(String value, int maxLength) {
        String normalized = normalizeOptionalRequestField(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw invalidRequest();
        }
        return normalized;
    }

    private String normalizeOptionalRequestField(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidRequest();
            }
        }
        if (!StringUtils.hasText(value)) {
            return null;
        }
        if (value.length() > maxLength) {
            throw invalidRequest();
        }
        String normalized = value.trim();
        return normalized;
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
        if (!decisionType(decision).equals(decisionType)) {
            throw invalidResult();
        }
        result.setDecision(decision);
        result.setDecisionType(decisionType);
        String hitCode = normalizeOptional(result.getHitCode(), HIT_CODE_MAX_LENGTH);
        result.setRiskTag(normalizeOptional(result.getRiskTag(), RISK_TAG_MAX_LENGTH));
        String reviewNo = normalizeOptional(result.getReviewNo(), REVIEW_NO_MAX_LENGTH);
        String eventNo = normalizeOptional(result.getEventNo(), EVENT_NO_MAX_LENGTH);
        if ("REVIEW".equals(decision) && !StringUtils.hasText(reviewNo)) {
            throw invalidResult();
        }
        if ("INTERCEPT".equals(decision)
                && (!StringUtils.hasText(eventNo) || !StringUtils.hasText(hitCode))) {
            throw invalidResult();
        }
        if ("REJECT".equals(decision)
                && !StringUtils.hasText(reviewNo)
                && (!StringUtils.hasText(eventNo) || !StringUtils.hasText(hitCode))) {
            throw invalidResult();
        }
        result.setHitCode(hitCode);
        result.setReviewNo(reviewNo);
        result.setEventNo(eventNo);
        normalizeOptional(result.getMessage(), MESSAGE_MAX_LENGTH);
        result.setMessage(decisionMessage(decision));
        return result;
    }

    private String decisionType(String decision) {
        if ("PASS".equals(decision)) {
            return "success";
        }
        if ("REVIEW".equals(decision)) {
            return "warn";
        }
        return "danger";
    }

    private String decisionMessage(String decision) {
        if ("PASS".equals(decision)) {
            return "风控准入通过";
        }
        if ("REVIEW".equals(decision)) {
            return "支付需要人工风控复核";
        }
        if ("REJECT".equals(decision)) {
            return "支付被风控拒绝";
        }
        return "支付被风控拦截";
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
        if (value.length() > maxLength) {
            throw invalidResult();
        }
        String normalized = value.trim();
        return normalized;
    }

    private BusinessException invalidResult() {
        return new BusinessException(
                ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE,
                "风控中心返回结果非法，无法确认支付准入状态");
    }

    private BusinessException invalidRequest() {
        return new BusinessException(
                ErrorCode.PAYMENT_RISK_SERVICE_UNAVAILABLE,
                "风控中心请求参数非法，无法发起支付准入评估");
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
