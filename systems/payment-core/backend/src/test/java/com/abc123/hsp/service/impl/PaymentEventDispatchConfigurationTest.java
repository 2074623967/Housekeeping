package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.IOException;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

class PaymentEventDispatchConfigurationTest {

    @Test
    void shouldConfigureBoundedHttpDispatchTimeout() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertEquals(
                Integer.valueOf(3000),
                resolver.getProperty("payment.event-dispatch.http-timeout-ms", Integer.class));
    }

    @Test
    void shouldUseLocalhostForLocalHttpIntegrationDefaults() throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load("application.yml", new ClassPathResource("application.yml"));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        PropertySourcesPropertyResolver resolver = new PropertySourcesPropertyResolver(propertySources);

        assertEquals(
                "http://localhost:8090/api/gateway/payment-orders/submit",
                resolver.getProperty("payment.submit.gateway-access.submit-url"));
        assertEquals(
                "http://localhost:8090/api/gateway/payment-orders/query",
                resolver.getProperty("payment.channel-query.gateway-access.query-url"));
        assertEquals(
                "http://localhost:18120/api/clearing/events/payments/success",
                resolver.getProperty("payment.downstream.clearing.payment-success-url"));
        assertEquals(
                "http://localhost:18110/api/accounting/events/payments/success",
                resolver.getProperty("payment.downstream.accounting.payment-success-url"));
        assertEquals(
                "http://localhost:18180/api/risk-control/decisions/evaluate",
                resolver.getProperty("payment.risk-control.evaluate-url"));
        assertEquals(
                "http://localhost:18170/api/ops-config/effective-snapshot",
                resolver.getProperty("payment.ops-config.snapshot-url"));
    }
}
