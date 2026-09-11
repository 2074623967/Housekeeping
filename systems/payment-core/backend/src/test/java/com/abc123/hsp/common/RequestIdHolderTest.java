package com.abc123.hsp.common;

import java.net.URL;
import java.net.URLClassLoader;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 请求追踪号生成器测试。
 */
class RequestIdHolderTest {

    @Test
    void shouldGenerateRequestIdInBusinessTimezone() throws Exception {
        ZoneId businessZone = ZoneId.of("Asia/Shanghai");
        LocalDate businessToday = LocalDate.now(businessZone);
        ZoneId alternativeZone = ZoneId.of("Pacific/Kiritimati");
        if (businessToday.equals(LocalDate.now(alternativeZone))) {
            alternativeZone = ZoneId.of("Etc/GMT+12");
        }
        Assertions.assertNotEquals(businessToday, LocalDate.now(alternativeZone));
        URL productionClasses = RequestIdHolder.class.getProtectionDomain()
                .getCodeSource().getLocation();

        TimeZone previousTimeZone = TimeZone.getDefault();
        try {
            TimeZone.setDefault(TimeZone.getTimeZone(alternativeZone));
            try (URLClassLoader classLoader = new URLClassLoader(
                    new URL[]{productionClasses}, null)) {
                Class<?> isolatedRequestIdHolder = Class.forName(
                        RequestIdHolder.class.getName(), true, classLoader);
                String requestId = (String) isolatedRequestIdHolder
                        .getMethod("nextRequestId").invoke(null);

                Assertions.assertTrue(requestId.startsWith(
                        "REQ" + businessToday.format(DateTimeFormatter.BASIC_ISO_DATE)));
            }
        } finally {
            TimeZone.setDefault(previousTimeZone);
        }
    }

    @Test
    void shouldGenerateUniqueRequestIdsConcurrently() {
        int requestCount = 10_000;

        Set<String> requestIds = IntStream.range(0, requestCount)
                .parallel()
                .mapToObj(index -> RequestIdHolder.nextRequestId())
                .collect(Collectors.toSet());

        Assertions.assertEquals(requestCount, requestIds.size());
        Assertions.assertTrue(requestIds.stream()
                .allMatch(requestId -> requestId.matches("REQ\\d{17}[0-9a-f]{8}")));
    }
}
