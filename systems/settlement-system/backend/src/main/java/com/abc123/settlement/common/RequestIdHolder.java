package com.abc123.settlement.common;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 生成简单请求号，便于联调排查。
 */
public final class RequestIdHolder {

    private static final AtomicLong SEQ = new AtomicLong(1000L);
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private RequestIdHolder() {
    }

    public static String nextRequestId() {
        return "REQ" + LocalDateTime.now().format(FORMATTER) + SEQ.incrementAndGet();
    }
}
