package com.abc123.clearing.common;

public final class ClearingEventHttpSignature {

    public static final String HEADER_NAME = "X-HSP-Clearing-Event-Signature";

    private ClearingEventHttpSignature() {
    }

    public static String sign(String body, String secret) {
        return PaymentEventHttpSignature.sign(body, secret);
    }
}
