package com.abc123.hsp.dto;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * 分页查询对象统一边界测试。
 */
class PageQueryBoundsTest {

    private static final int MIN_EXPECTED_PAGED_QUERY_COUNT = 16;

    @Test
    void shouldKeepEveryPageQueryWithinSafeOffsetBounds() throws Exception {
        Resource[] resources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:com/abc123/hsp/dto/*QueryDTO.class");
        Set<String> classNames = new LinkedHashSet<>();
        for (Resource resource : resources) {
            String filename = resource.getFilename();
            if (filename != null && filename.endsWith(".class") && !filename.contains("$")) {
                classNames.add("com.abc123.hsp.dto."
                        + filename.substring(0, filename.length() - ".class".length()));
            }
        }

        int checkedQueryCount = 0;
        for (String className : classNames) {
            Class<?> queryType = Class.forName(className);
            Method getOffset;
            try {
                getOffset = queryType.getMethod("getOffset");
            } catch (NoSuchMethodException ignored) {
                continue;
            }
            Object query = queryType.getDeclaredConstructor().newInstance();
            Method setPageNo = queryType.getMethod("setPageNo", int.class);
            Method setPageSize = queryType.getMethod("setPageSize", int.class);
            Method getPageNo = queryType.getMethod("getPageNo");

            setPageNo.invoke(query, Integer.MAX_VALUE);
            setPageSize.invoke(query, Integer.MAX_VALUE);
            Assertions.assertEquals(PageQueryBounds.MAX_PAGE_NO, getPageNo.invoke(query), className);
            Assertions.assertEquals(99900, getOffset.invoke(query), className);

            setPageNo.invoke(query, Integer.MIN_VALUE);
            setPageSize.invoke(query, Integer.MIN_VALUE);
            Assertions.assertEquals(1, getPageNo.invoke(query), className);
            Assertions.assertEquals(0, getOffset.invoke(query), className);
            checkedQueryCount++;
        }

        Assertions.assertTrue(
                checkedQueryCount >= MIN_EXPECTED_PAGED_QUERY_COUNT,
                "all paged query DTOs must be discoverable");
    }

    @Test
    void shouldNormalizePageSizeForMapperLimitParameters() {
        Assertions.assertEquals(1, PageQueryBounds.normalizePageSize(Integer.MIN_VALUE));
        Assertions.assertEquals(20, PageQueryBounds.normalizePageSize(20));
        Assertions.assertEquals(100, PageQueryBounds.normalizePageSize(Integer.MAX_VALUE));

        PaymentDeadLetterTaskQueryDTO deadLetterQuery = new PaymentDeadLetterTaskQueryDTO();
        deadLetterQuery.setPageSize(Integer.MAX_VALUE);
        Assertions.assertEquals(100, deadLetterQuery.getLimit());
    }
}
