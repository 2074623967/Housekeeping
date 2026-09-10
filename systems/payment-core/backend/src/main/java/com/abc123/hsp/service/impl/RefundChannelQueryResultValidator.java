package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.RefundChannelQueryResultDTO;
import java.util.Locale;
import org.springframework.util.StringUtils;

/**
 * 退款渠道查单结果边界校验。
 */
final class RefundChannelQueryResultValidator {

    private static final int STATUS_MAX_LENGTH = 32;
    private static final int CHANNEL_REFUND_NO_MAX_LENGTH = 128;
    private static final int QUERY_SOURCE_MAX_LENGTH = 64;

    private RefundChannelQueryResultValidator() {
    }

    static RefundChannelQueryResultDTO validateAndNormalize(RefundChannelQueryResultDTO result) {
        if (result == null
                || !StringUtils.hasText(result.getRefundStatus())
                || !StringUtils.hasText(result.getChannelRefundNo())
                || !StringUtils.hasText(result.getQuerySource())) {
            throw queryFailed("refund channel query result is incomplete");
        }
        result.setRefundStatus(normalize(result.getRefundStatus(), STATUS_MAX_LENGTH)
                .toUpperCase(Locale.ROOT));
        result.setChannelRefundNo(normalize(result.getChannelRefundNo(), CHANNEL_REFUND_NO_MAX_LENGTH));
        result.setQuerySource(normalize(result.getQuerySource(), QUERY_SOURCE_MAX_LENGTH));
        if (!"PROCESSING".equals(result.getRefundStatus())
                && !"SUCCESS".equals(result.getRefundStatus())
                && !"FAIL".equals(result.getRefundStatus())) {
            throw queryFailed("refund channel query status is unsupported");
        }
        return result;
    }

    private static String normalize(String value, int maxLength) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw queryFailed("refund channel query field contains control characters");
            }
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw queryFailed("refund channel query field is too long");
        }
        return normalized;
    }

    private static BusinessException queryFailed(String message) {
        return new BusinessException(ErrorCode.REFUND_CHANNEL_QUERY_FAILED, message);
    }
}
