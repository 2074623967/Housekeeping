package com.abc123.hsp.dto;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * 统一分页结果测试。
 */
class PageResultDTOTest {

    @Test
    void shouldNormalizeNullItemsToEmptyList() {
        PageResultDTO<String> result = new PageResultDTO<>(null, 0L, 1, 20);

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }
}
