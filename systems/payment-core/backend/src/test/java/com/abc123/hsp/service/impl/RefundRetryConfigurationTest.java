package com.abc123.hsp.service.impl;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * 失败退款重试环境默认值契约测试。
 */
class RefundRetryConfigurationTest {

    @Test
    void shouldUseBoundedRefundRetryBatchByDefault() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        Assertions.assertEquals(
                Integer.valueOf(100),
                resolver.getProperty("payment.refund-retry.batch-size", Integer.class)
        );
    }
}
