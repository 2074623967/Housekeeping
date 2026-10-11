package com.abc123.hsp.service.support;

import java.util.Collections;
import java.util.List;

/** Enforces the synchronous CSV export row boundary. */
public final class CsvExportRowLimiter {

    public static final int MAX_ROWS = 1000;
    public static final int QUERY_ROWS = MAX_ROWS + 1;

    private CsvExportRowLimiter() {
    }

    public static <T> List<T> requireWithinLimit(List<T> rows) {
        if (rows == null) {
            return Collections.emptyList();
        }
        requireWithinLimit(rows.size());
        return rows;
    }

    public static int requireWithinLimit(int rowCount) {
        if (rowCount > MAX_ROWS) {
            throw new IllegalArgumentException("导出数据超过1000条，请缩小筛选范围");
        }
        return rowCount;
    }
}
