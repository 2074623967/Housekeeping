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
    public static final String PAYMENT_CHANNEL_SUBMIT_ADAPTER_MISSING = "PAYMENT-1012";
    public static final String PAYMENT_REQUESTED_CHANNEL_INVALID = "PAYMENT-1013";
    public static final String PAYMENT_CHANNEL_SCENE_MISMATCH = "PAYMENT-1014";
    public static final String PAYMENT_CHANNEL_DAILY_LIMIT_EXCEEDED = "PAYMENT-1015";
    public static final String PAYMENT_CALLBACK_SIGNATURE_INVALID = "PAYMENT-1005";
    public static final String PAYMENT_CALLBACK_SECRET_MISSING = "PAYMENT-1006";
    public static final String PAYMENT_CALLBACK_NONCE_REPLAY = "PAYMENT-1007";
    public static final String PAYMENT_CALLBACK_TIMESTAMP_INVALID = "PAYMENT-1008";
    public static final String PAYMENT_CALLBACK_CHANNEL_MISSING = "PAYMENT-1009";
    public static final String PAYMENT_CHANNEL_UNAVAILABLE = "PAYMENT-1010";
    public static final String PAYMENT_ORDER_SOURCE_MISSING = "PAYMENT-1011";
    public static final String PAYMENT_SUBMIT_IN_PROGRESS = "PAYMENT-1016";
    public static final String PAYMENT_SOURCE_APP_NOT_ALLOWED = "PAYMENT-1017";
    public static final String PAYMENT_SUBMIT_RATE_LIMITED = "PAYMENT-1018";
    public static final String PAYMENT_SUBMIT_SELF_CHECK_BLOCKED = "PAYMENT-1019";
    public static final String PAYMENT_MERCHANT_NOT_ALLOWED = "PAYMENT-1020";
    public static final String PAYMENT_ACCESS_TOKEN_INVALID = "PAYMENT-1021";
    public static final String PAYMENT_SUBMIT_CONCURRENCY_BLOCKED = "PAYMENT-1022";
    public static final String PAYMENT_SUBMIT_INTERFACE_RATE_LIMITED = "PAYMENT-1023";
    public static final String PAYMENT_RISK_SERVICE_UNAVAILABLE = "PAYMENT-1024";
    public static final String PAYMENT_OPS_CONFIG_SNAPSHOT_INVALID = "PAYMENT-1025";
    public static final String PAYMENT_CHANNEL_QUERY_FAILED = "PAYMENT-1026";
    public static final String PAYMENT_CHANNEL_QUERY_RATE_LIMITED = "PAYMENT-1027";
    public static final String PAYMENT_SUBMIT_IDEMPOTENCY_KEY_INVALID = "PAYMENT-1028";
    public static final String PAYMENT_CLOSE_CHANNEL_CONFIRMATION_REQUIRED = "PAYMENT-1029";
    public static final String PAYMENT_CALLBACK_CHANNEL_MISMATCH = "PAYMENT-1030";
    public static final String PAYMENT_CALLBACK_TRANSACTION_MISMATCH = "PAYMENT-1031";
    public static final String PAYMENT_CALLBACK_ALGORITHM_INVALID = "PAYMENT-1032";
    public static final String PAYMENT_CALLBACK_CREDENTIAL_INVALID = "PAYMENT-1033";
    public static final String PAYMENT_CALLBACK_SECURITY_CONFIG_INVALID = "PAYMENT-1034";
    public static final String PAYMENT_SUBMIT_REQUEST_INVALID = "PAYMENT-1035";
    public static final String PAYMENT_REQUEST_INVALID = "PAYMENT-1036";
    public static final String PAYMENT_ORDER_ALREADY_SETTLED = "PAYMENT-1037";
    public static final String PAYMENT_PREPAY_REQUEST_CONFLICT = "PAYMENT-1038";
    public static final String REFUND_CALLBACK_REQUEST_INVALID = "PAYMENT-1039";
    public static final String REFUND_CALLBACK_CHANNEL_MISMATCH = "PAYMENT-1040";
    public static final String REFUND_CALLBACK_TRANSACTION_MISMATCH = "PAYMENT-1041";
    public static final String REFUND_CALLBACK_STATUS_INVALID = "PAYMENT-1042";
    public static final String REFUND_CALLBACK_FINAL_STATUS_CONFLICT = "PAYMENT-1043";
    public static final String REFUND_ORDER_NOT_FOUND = "PAYMENT-1044";
    public static final String REFUND_CHANNEL_QUERY_ADAPTER_MISSING = "PAYMENT-1045";
    public static final String REFUND_CHANNEL_QUERY_FAILED = "PAYMENT-1046";
    public static final String REFUND_CHANNEL_QUERY_RATE_LIMITED = "PAYMENT-1047";
    public static final String PAYMENT_CHANNEL_SUBMIT_FAILED = "PAYMENT-1048";
    public static final String REFUND_CHANNEL_SUBMIT_FAILED = "PAYMENT-1049";
    public static final String REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED = "PAYMENT-1050";
    public static final String PAYMENT_ISSUE_ALERT_RECEIPT_QUERY_RATE_LIMITED = "PAYMENT-1051";
    public static final String REFUND_APPLICATION_IDEMPOTENCY_KEY_INVALID = "PAYMENT-1052";
    public static final String REFUND_APPLICATION_IDEMPOTENCY_CONFLICT = "PAYMENT-1053";
    public static final String PAYMENT_CHANNEL_SUBMISSION_FACT_MISSING = "PAYMENT-1054";
    public static final String ORDER_NOT_FOUND = "PAYMENT-1055";
    public static final String BILL_NOT_FOUND = "PAYMENT-1056";
    public static final String CASHIER_SESSION_NOT_FOUND = "PAYMENT-1057";
    public static final String PAYMENT_REQUEST_NOT_FOUND = "PAYMENT-1058";
    public static final String PAYMENT_FLOW_NOT_FOUND = "PAYMENT-1059";
    public static final String PAYMENT_LOG_NOT_FOUND = "PAYMENT-1060";
    public static final String PAYMENT_EVENT_NOT_FOUND = "PAYMENT-1061";
}
