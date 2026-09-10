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
 * 支付超时关单环境默认值契约测试。
 */
class PaymentExpiryConfigurationTest {

    @Test
    void shouldUseBoundedExpiryCloseBatchByDefault() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        Assertions.assertEquals(
                Integer.valueOf(100),
                resolver.getProperty("payment.expiry-close.batch-size", Integer.class)
        );
        Assertions.assertEquals(Integer.valueOf(60), resolver.getProperty(
                "payment.expiry-close.retry-interval-seconds", Integer.class));
        Assertions.assertEquals(Integer.valueOf(2), resolver.getProperty(
                "payment.expiry-close.retry-backoff-factor", Integer.class));
        Assertions.assertEquals(Integer.valueOf(3600), resolver.getProperty(
                "payment.expiry-close.max-retry-interval-seconds", Integer.class));
    }
}
