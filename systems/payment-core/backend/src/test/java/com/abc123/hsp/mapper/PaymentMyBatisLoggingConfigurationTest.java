package com.abc123.hsp.mapper;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

/**
 * MyBatis 参数日志安全默认值测试。
 */
class PaymentMyBatisLoggingConfigurationTest {

    @Test
    void shouldDisableSqlParameterLoggingByDefault() throws IOException {
        List<PropertySource<?>> propertySources = new YamlPropertySourceLoader()
                .load("application", new ClassPathResource("application.yml"));

        Object logImpl = null;
        for (PropertySource<?> propertySource : propertySources) {
            Object configuredValue = propertySource.getProperty("mybatis.configuration.log-impl");
            if (configuredValue != null) {
                logImpl = configuredValue;
                break;
            }
        }

        Assertions.assertEquals("org.apache.ibatis.logging.nologging.NoLoggingImpl", logImpl);
    }
}
