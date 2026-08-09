package com.abc123.refund.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成接口请求追踪号。
 */
public final class RequestIdHolder {

    private static final AtomicLong SEQUENCE = new AtomicLong();
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    private RequestIdHolder() {
    }

    public static String nextRequestId() {
        return "REF-REQ-" + LocalDateTime.now().format(FORMATTER) + "-" + SEQUENCE.incrementAndGet();
    }
}

