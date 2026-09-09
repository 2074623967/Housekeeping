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
 * 回调验签环境默认值契约测试。
 */
class PaymentCallbackSignatureConfigurationTest {

    @Test
    void shouldRequireCallbackSignatureByDefault() throws IOException {
        PropertySourcesPropertyResolver resolver = load("application.yml");

        Assertions.assertEquals(
                Boolean.TRUE,
                resolver.getProperty("payment.callback.require-signature", Boolean.class)
        );
        Assertions.assertNull(resolver.getProperty("payment.callback.secret"));
        Assertions.assertNull(resolver.getProperty("payment.callback.allowed-skew-seconds"));
        Assertions.assertEquals(
                Long.valueOf(601L),
                resolver.getProperty("payment.callback.nonce-ttl-seconds", Long.class)
        );
        Assertions.assertEquals(
                Long.valueOf(300000L),
                resolver.getProperty("payment.callback.nonce-cleanup.fixed-delay-ms", Long.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(1000),
                resolver.getProperty("payment.callback.nonce-cleanup.batch-size", Integer.class)
        );
    }

    @Test
    void shouldDisableCallbackSignatureOnlyForDemoProfile() throws IOException {
        PropertySourcesPropertyResolver resolver = load("application-demo.yml");

        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.callback.require-signature", Boolean.class)
        );
    }

    private PropertySourcesPropertyResolver load(String resourceName) throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load(resourceName, new ClassPathResource(resourceName));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
