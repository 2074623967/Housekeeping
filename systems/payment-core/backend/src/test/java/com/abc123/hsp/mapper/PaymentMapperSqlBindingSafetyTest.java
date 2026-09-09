package com.abc123.hsp.mapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.util.StreamUtils;

/**
 * Mapper SQL 参数绑定安全约束测试。
 */
class PaymentMapperSqlBindingSafetyTest {

    @Test
    void shouldRejectRawStringSubstitutionInEveryMapperXml() throws IOException {
        Resource[] mapperResources = new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/*.xml");

        Assertions.assertTrue(mapperResources.length > 0, "mapper XML resources must be discoverable");
        for (Resource mapperResource : mapperResources) {
            String mapperXml;
            try (InputStream inputStream = mapperResource.getInputStream()) {
                mapperXml = StreamUtils.copyToString(inputStream, StandardCharsets.UTF_8);
            }
            Assertions.assertFalse(
                    mapperXml.contains("${"),
                    () -> "raw MyBatis substitution is forbidden: " + mapperResource.getFilename());
        }
    }
}
