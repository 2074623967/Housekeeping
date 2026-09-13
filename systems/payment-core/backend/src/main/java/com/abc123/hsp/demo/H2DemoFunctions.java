package com.abc123.hsp.demo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * MySQL compatibility functions used only by the H2-backed demo profile.
 */
public final class H2DemoFunctions {

    private H2DemoFunctions() {
    }

    public static String format(BigDecimal value, int scale) {
        if (value == null) {
            return null;
        }
        StringBuilder pattern = new StringBuilder("#,##0");
        if (scale > 0) {
            pattern.append('.');
            for (int index = 0; index < scale; index++) {
                pattern.append('0');
            }
        }
        DecimalFormat formatter = new DecimalFormat(pattern.toString());
        formatter.setRoundingMode(RoundingMode.HALF_UP);
        return formatter.format(value);
    }

    public static String dateFormat(Timestamp timestamp, String pattern) {
        if (timestamp == null) {
            return null;
        }
        return timestamp.toLocalDateTime().format(DateTimeFormatter.ofPattern(toJavaPattern(pattern)));
    }

    public static int findInSet(String value, String values) {
        if (value == null || values == null) {
            return 0;
        }
        String[] candidates = values.split(",");
        for (int index = 0; index < candidates.length; index++) {
            if (value.equals(candidates[index])) {
                return index + 1;
            }
        }
        return 0;
    }

    public static int weekday(Date date) {
        return date == null ? 0 : date.toLocalDate().getDayOfWeek().getValue() - 1;
    }

    public static Timestamp strToDate(String value, String pattern) {
        if (value == null) {
            return null;
        }
        LocalDateTime parsed = LocalDateTime.parse(value, DateTimeFormatter.ofPattern(toJavaPattern(pattern)));
        return Timestamp.valueOf(parsed);
    }

    private static String toJavaPattern(String pattern) {
        return pattern
                .replace("%Y", "yyyy")
                .replace("%m", "MM")
                .replace("%d", "dd")
                .replace("%H", "HH")
                .replace("%i", "mm")
                .replace("%s", "ss");
    }
}
