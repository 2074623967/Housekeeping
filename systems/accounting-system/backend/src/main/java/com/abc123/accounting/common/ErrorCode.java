package com.abc123.accounting.common;

/**
 * 账务系统错误码常量。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION_ERROR = "ACCOUNTING-400";
    public static final String SYSTEM_ERROR = "ACCOUNTING-500";
    public static final String ACCOUNT_NOT_FOUND = "ACCOUNTING-1001";
}
