package com.abc123.settlement.common;

/**
 * 结算系统错误码常量。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION_ERROR = "SETTLEMENT-400";
    public static final String SYSTEM_ERROR = "SETTLEMENT-500";
    public static final String SETTLEMENT_ORDER_NOT_FOUND = "SETTLEMENT-1001";
    public static final String PAYOUT_BATCH_NOT_FOUND = "SETTLEMENT-1002";
}
