package com.abc123.settlement.dto;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PageResultDTOTest {

    @Test
    void shouldNormalizeNullItemsToEmptyList() {
        PageResultDTO<String> result = new PageResultDTO<>(null, 0, 1, 20);

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }
}
