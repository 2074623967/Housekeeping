package com.abc123.hsp.common;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ThreadLocalRandom;

public final class RequestIdHolder {

    private static final SimpleDateFormat FORMATTER = new SimpleDateFormat("yyyyMMddHHmmssSSS");

    private RequestIdHolder() {
    }

    public static String nextRequestId() {
        return "REQ" + FORMATTER.format(new Date()) + ThreadLocalRandom.current().nextInt(100, 1000);
    }
}
