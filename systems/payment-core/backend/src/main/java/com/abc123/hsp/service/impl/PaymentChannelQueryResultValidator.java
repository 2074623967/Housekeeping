package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentChannelQueryResultDTO;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 收口渠道查单结果，避免外部响应或配置映射结果越过数据库与审计字段边界。
 */
final class PaymentChannelQueryResultValidator {

    private static final int TRADE_STATUS_MAX_LENGTH = 32;
    private static final int CHANNEL_TRANSACTION_NO_MAX_LENGTH = 128;
    private static final int QUERY_SOURCE_MAX_LENGTH = 64;
    private static final int CHANNEL_RETURN_CODE_MAX_LENGTH = 64;
    private static final int STANDARDIZED_CODE_MAX_LENGTH = 64;
    private static final int STANDARDIZED_MESSAGE_MAX_LENGTH = 256;
    private static final int HANDLING_SUGGESTION_MAX_LENGTH = 256;
    private static final int BOOLEAN_TEXT_MAX_LENGTH = 16;
    private static final int MAPPING_VERSION_MAX_LENGTH = 32;
    private static final List<String> TRADE_STATUSES = Arrays.asList(
            "PREPAY_CREATED",
            "RISK_REVIEW",
            "RISK_BLOCKED",
            "RISK_REJECTED",
            "WAIT_CALLBACK",
            "SUCCESS",
            "CLOSED",
            "FAIL");

    private PaymentChannelQueryResultValidator() {
    }

    static PaymentChannelQueryResultDTO validateAndNormalize(PaymentChannelQueryResultDTO result) {
        if (result == null) {
            throw invalidResult();
        }
        String tradeStatus = normalizeRequired(result.getTradeStatus(), TRADE_STATUS_MAX_LENGTH)
                .toUpperCase(Locale.ROOT);
        if (!TRADE_STATUSES.contains(tradeStatus)) {
            throw invalidResult();
        }
        result.setTradeStatus(tradeStatus);
        result.setChannelTransactionNo(normalizeOptional(
                result.getChannelTransactionNo(), CHANNEL_TRANSACTION_NO_MAX_LENGTH));
        result.setQuerySource(normalizeRequired(result.getQuerySource(), QUERY_SOURCE_MAX_LENGTH));
        result.setChannelReturnCode(normalizeOptional(
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
        return result;
    }

    private static String normalizeRequired(String value, int maxLength) {
        String normalized = normalizeOptional(value, maxLength);
        if (normalized == null) {
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
        return new BusinessException(
                ErrorCode.PAYMENT_CHANNEL_QUERY_FAILED,
                "payment channel query returned invalid result");
    }
}
