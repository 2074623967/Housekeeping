package com.abc123.hsp.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import org.apache.ibatis.io.Resources;
import org.junit.jupiter.api.Test;

/**
 * 日终账期 SQL 的可索引半开区间契约测试。
 */
class PaymentDayEndBusinessDateRangeContractTest {

    private static final Pattern FUNCTION_WRAPPED_BIZ_DATE_COLUMN = Pattern.compile(
            "DATE\\([^)]*\\)\\s*=\\s*#\\{bizDate}", Pattern.CASE_INSENSITIVE);
    private static final String EXCLUSIVE_END =
            "&lt; TIMESTAMPADD(DAY, 1, CAST(#{bizDate} AS DATETIME))";
    private static final String INCLUSIVE_START =
            "&gt;= CAST(#{bizDate} AS DATETIME)";

    @Test
    void shouldKeepEveryBusinessDatePredicateSargable() throws Exception {
        String mapperXml;
        try (InputStream input = Resources.getResourceAsStream("mapper/PaymentDayEndMapper.xml")) {
            mapperXml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertFalse(FUNCTION_WRAPPED_BIZ_DATE_COLUMN.matcher(mapperXml).find());
        assertEquals(22, countOccurrences(mapperXml, INCLUSIVE_START));
        assertEquals(22, countOccurrences(mapperXml, EXCLUSIVE_END));
        assertTrue(mapperXml.contains(
                "<select id=\"countPaymentSuccessFactMismatchByDate\" resultType=\"java.lang.Integer\">\n"
                        + "        SELECT COUNT(1)\n"
                        + "        FROM t_payment_order payment_order\n"
                        + "        WHERE payment_order.success_at &gt;= CAST(#{bizDate} AS DATETIME)\n"
                        + "          AND payment_order.success_at &lt; "
                        + "TIMESTAMPADD(DAY, 1, CAST(#{bizDate} AS DATETIME))\n"));
    }

    private int countOccurrences(String value, String expected) {
        int count = 0;
        int offset = 0;
        while ((offset = value.indexOf(expected, offset)) >= 0) {
            count++;
            offset += expected.length();
        }
        return count;
    }
}
