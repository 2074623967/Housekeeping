package com.abc123.gatewayaccess.common;

import java.util.UUID;

/**
 * 请求号生成器。
 */
public final class RequestIdHolder {

    private RequestIdHolder() {
    }

    public static String requestId() {
        return "REQ-" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }
}
