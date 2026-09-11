package com.abc123.hsp.common;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class RequestIdHolder {

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter
            .ofPattern("yyyyMMddHHmmssSSS")
            .withZone(ZoneId.of("Asia/Shanghai"));

    private RequestIdHolder() {
    }

    public static String nextRequestId() {
        String uniqueSuffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return "REQ" + FORMATTER.format(Instant.now()) + uniqueSuffix;
    }
}
