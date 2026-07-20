package com.abc123.hsp.common;

/**
 * 支付核心域错误码常量。
 */
public final class ErrorCode {

    private ErrorCode() {
    }

    public static final String VALIDATION_ERROR = "PAYMENT-400";
    public static final String SYSTEM_ERROR = "PAYMENT-500";

    public static final String PREPAY_ORDER_NOT_FOUND = "PAYMENT-1001";
    public static final String PAYMENT_ORDER_NOT_FOUND = "PAYMENT-1002";
    public static final String PAYMENT_ROUTE_UNSUPPORTED = "PAYMENT-1003";
    public static final String PAYMENT_CHANNEL_QUERY_ADAPTER_MISSING = "PAYMENT-1004";
    public static final String PAYMENT_CALLBACK_SIGNATURE_INVALID = "PAYMENT-1005";
    public static final String PAYMENT_CALLBACK_SECRET_MISSING = "PAYMENT-1006";
    public static final String PAYMENT_CALLBACK_NONCE_REPLAY = "PAYMENT-1007";
    public static final String PAYMENT_CALLBACK_TIMESTAMP_INVALID = "PAYMENT-1008";
    public static final String PAYMENT_CALLBACK_CHANNEL_MISSING = "PAYMENT-1009";
    public static final String PAYMENT_CHANNEL_UNAVAILABLE = "PAYMENT-1010";
    public static final String PAYMENT_ORDER_SOURCE_MISSING = "PAYMENT-1011";
}
