package com.abc123.hsp.service.support;

/**
 * 运营列表排序参数归一化工具。
 */
public final class QuerySortInputNormalizer {

    private static final int SORT_FIELD_MAX_LENGTH = 64;
    private static final int SORT_ORDER_MAX_LENGTH = 16;

    private QuerySortInputNormalizer() {
    }

    public static String normalizeSortField(String value, String defaultValue) {
        return normalize(value, defaultValue, SORT_FIELD_MAX_LENGTH, "排序字段").trim();
    }

    public static String normalizeSortOrder(String value, String defaultValue) {
        return normalize(value, defaultValue, SORT_ORDER_MAX_LENGTH, "排序方向")
                .trim()
                .toLowerCase();
    }

    private static String normalize(String value, String defaultValue, int maxLength, String fieldName) {
        if (value == null) {
            return defaultValue;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }
}
