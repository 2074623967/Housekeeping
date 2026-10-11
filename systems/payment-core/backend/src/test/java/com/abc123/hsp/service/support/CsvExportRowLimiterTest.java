package com.abc123.hsp.service.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class CsvExportRowLimiterTest {

    @Test
    void shouldReturnEmptyListForNullRows() {
        assertTrue(CsvExportRowLimiter.requireWithinLimit(null).isEmpty());
    }

    @Test
    void shouldAllowRowsAtLimitWithoutCopying() {
        List<String> rows = Collections.nCopies(CsvExportRowLimiter.MAX_ROWS, "row");

        assertSame(rows, CsvExportRowLimiter.requireWithinLimit(rows));
        assertEquals(1001, CsvExportRowLimiter.QUERY_ROWS);
    }

    @Test
    void shouldRejectRowsAboveLimit() {
        List<String> rows = Collections.nCopies(CsvExportRowLimiter.QUERY_ROWS, "row");

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> CsvExportRowLimiter.requireWithinLimit(rows));

        assertTrue(exception.getMessage().contains("超过1000条"));
    }

    @Test
    void shouldCheckCombinedRowCount() {
        assertEquals(1000, CsvExportRowLimiter.requireWithinLimit(1000));
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class, () -> CsvExportRowLimiter.requireWithinLimit(1001));
        assertTrue(exception.getMessage().contains("超过1000条"));
    }
}
