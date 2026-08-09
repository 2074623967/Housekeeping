package com.abc123.walletaccount.common;

import java.util.UUID;

public final class RequestIdHolder {

    private RequestIdHolder() {
    }

    public static String nextRequestId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
