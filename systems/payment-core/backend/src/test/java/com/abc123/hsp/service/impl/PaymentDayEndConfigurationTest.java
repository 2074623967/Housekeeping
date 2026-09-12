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
 * 支付日终自动调度配置测试。
 */
class PaymentDayEndConfigurationTest {

    @Test
    void shouldKeepAutomaticDayEndDisabledWithShanghaiScheduleByDefault() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.day-end.auto.enabled", Boolean.class));
        Assertions.assertEquals("0 30 0 * * *", resolver.getProperty("payment.day-end.auto.cron"));
    }
}
