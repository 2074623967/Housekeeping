package com.abc123.hsp.common;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * 请求追踪号生成器测试。
 */
class RequestIdHolderTest {

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
