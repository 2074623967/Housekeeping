package com.abc123.clearing.common;

public final class ClearingEventHttpSignature {

    public static final String HEADER_NAME = "X-HSP-Clearing-Event-Signature";
    public static final String TIMESTAMP_HEADER_NAME = "X-HSP-Clearing-Event-Timestamp";

    private ClearingEventHttpSignature() {
    }

    public static String sign(String body, String timestamp, String secret) {
        return PaymentEventHttpSignature.signTimed(body, timestamp, secret);
    }
}
