package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelSubmitResultDTO;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.util.StringUtils;

/**
 * 支付渠道提交结果边界校验。
 */
final class PaymentChannelSubmitResultValidator {

    private static final int CHANNEL_TRANSACTION_NO_MAX_LENGTH = 128;
    private static final int ATTEMPT_STATUS_MAX_LENGTH = 32;
    private static final int ATTEMPT_STATUS_TYPE_MAX_LENGTH = 32;
    private static final int RESPONSE_PAYLOAD_MAX_LENGTH = 2048;
    private static final int CHANNEL_RETURN_CODE_MAX_LENGTH = 64;
    private static final int STANDARDIZED_CODE_MAX_LENGTH = 64;
    private static final int STANDARDIZED_MESSAGE_MAX_LENGTH = 256;
    private static final int HANDLING_SUGGESTION_MAX_LENGTH = 256;
    private static final int BOOLEAN_TEXT_MAX_LENGTH = 16;
    private static final int MAPPING_VERSION_MAX_LENGTH = 32;
    private static final int NATIVE_PAYMENT_PARAM_MAX_LENGTH = 512;
    private static final List<String> NATIVE_PAYMENT_PARAM_FIELDS = Arrays.asList(
            "timeStamp", "nonceStr", "package", "signType", "paySign");
    private static final List<String> ATTEMPT_STATUS_TYPES = Arrays.asList(
            "info", "warn", "danger", "success");

    private PaymentChannelSubmitResultValidator() {
    }

    static PaymentChannelSubmitResultDTO validateAndNormalize(PaymentChannelSubmitResultDTO result) {
        if (result == null) {
            throw invalidResult();
        }
        result.setChannelTransactionNo(normalizeRequired(
                result.getChannelTransactionNo(), CHANNEL_TRANSACTION_NO_MAX_LENGTH));
        result.setAttemptStatus(normalizeRequired(result.getAttemptStatus(), ATTEMPT_STATUS_MAX_LENGTH));
        String attemptStatusType = normalizeRequired(
                result.getAttemptStatusType(), ATTEMPT_STATUS_TYPE_MAX_LENGTH)
                .toLowerCase(Locale.ROOT);
        if (!ATTEMPT_STATUS_TYPES.contains(attemptStatusType)) {
            throw invalidResult();
        }
        result.setAttemptStatusType(attemptStatusType);
        result.setResponsePayload(normalizeOptional(result.getResponsePayload(), RESPONSE_PAYLOAD_MAX_LENGTH));
        result.setChannelReturnCode(normalizeRequired(
                result.getChannelReturnCode(), CHANNEL_RETURN_CODE_MAX_LENGTH));
        result.setStandardizedCode(normalizeOptional(
                result.getStandardizedCode(), STANDARDIZED_CODE_MAX_LENGTH));
        result.setStandardizedMessage(normalizeOptional(
                result.getStandardizedMessage(), STANDARDIZED_MESSAGE_MAX_LENGTH));
        result.setHandlingSuggestion(normalizeOptional(
                result.getHandlingSuggestion(), HANDLING_SUGGESTION_MAX_LENGTH));
        result.setRetryable(normalizeOptional(result.getRetryable(), BOOLEAN_TEXT_MAX_LENGTH));
        result.setManualInterventionRequired(normalizeOptional(
                result.getManualInterventionRequired(), BOOLEAN_TEXT_MAX_LENGTH));
        result.setMappingVersion(normalizeOptional(result.getMappingVersion(), MAPPING_VERSION_MAX_LENGTH));
        result.setNativePaymentParams(normalizeNativePaymentParams(result.getNativePaymentParams()));
        return result;
    }

    private static Map<String, String> normalizeNativePaymentParams(Map<String, String> params) {
        if (params == null || params.isEmpty()) {
            return null;
        }
        Map<String, String> normalized = new HashMap<>();
        for (String field : NATIVE_PAYMENT_PARAM_FIELDS) {
            String value = params.get(field);
            String normalizedValue = normalizeOptional(value, NATIVE_PAYMENT_PARAM_MAX_LENGTH);
            if (!StringUtils.hasText(normalizedValue)) {
                throw invalidResult();
            }
            normalized.put(field, normalizedValue);
        }
        return normalized;
    }

    private static String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (!StringUtils.hasText(normalized)) {
            throw invalidResult();
        }
        return normalized;
    }

    private static String normalizeOptional(String value, int maxLength) {
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

    private static BusinessException invalidResult() {
        return BusinessException.sanitized(
                ErrorCode.PAYMENT_CHANNEL_SUBMIT_FAILED,
                "payment channel submit result is invalid");
    }
}
