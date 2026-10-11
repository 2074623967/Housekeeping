package com.abc123.hsp.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

/**
 * 运营列表排序参数边界测试。
 */
class QuerySortInputNormalizerTest {

    @Test
    void shouldNormalizeSortFieldAndOrder() {
        assertEquals("createdAt", QuerySortInputNormalizer.normalizeSortField(" createdAt ", "createdAt"));
        assertEquals("asc", QuerySortInputNormalizer.normalizeSortOrder(" ASC ", "desc"));
        assertEquals("createdAt", QuerySortInputNormalizer.normalizeSortField(null, "createdAt"));
        assertEquals("desc", QuerySortInputNormalizer.normalizeSortOrder(null, "desc"));
    }

    @Test
    void shouldRejectOversizedSortInputsBeforeMapperAccess() {
        assertThrows(IllegalArgumentException.class,
                () -> QuerySortInputNormalizer.normalizeSortField(repeat('s', 64) + " ", "createdAt"));
        assertThrows(IllegalArgumentException.class,
                () -> QuerySortInputNormalizer.normalizeSortOrder(repeat('a', 16) + " ", "desc"));
    }

    @Test
    void shouldRejectControlCharactersInSortInputs() {
        assertThrows(IllegalArgumentException.class,
                () -> QuerySortInputNormalizer.normalizeSortField("createdAt\n", "createdAt"));
        assertThrows(IllegalArgumentException.class,
                () -> QuerySortInputNormalizer.normalizeSortOrder("desc\r", "desc"));
    }

    private static String repeat(char value, int count) {
        StringBuilder builder = new StringBuilder(count);
        for (int index = 0; index < count; index++) {
            builder.append(value);
        }
        return builder.toString();
    }
}
