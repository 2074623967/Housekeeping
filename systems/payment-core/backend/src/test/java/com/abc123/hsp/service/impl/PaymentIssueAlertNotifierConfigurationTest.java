package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentIssueAlertDispatchItemDTO;
import com.abc123.hsp.service.PaymentIssueAlertNotifier;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.PropertySourcesPropertyResolver;
import org.springframework.core.io.ClassPathResource;

/**
 * 告警通知器本地回执环境默认值契约测试。
 */
class PaymentIssueAlertNotifierConfigurationTest {

    @Test
    void shouldDisableLocalReceiptByDefault() throws IOException {
        PropertySourcesPropertyResolver resolver = load("application.yml");

        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.issue-alert.local-receipt-enabled", Boolean.class)
        );
        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.issue-alert.receipt-reconcile.enabled", Boolean.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(100),
                resolver.getProperty("payment.issue-alert.dispatch.batch-size", Integer.class)
        );
        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.issue-alert.receipt-query.gateway-access.enabled", Boolean.class)
        );
        Assertions.assertEquals(
                "",
                resolver.getProperty("payment.issue-alert.receipt-query.gateway-access.query-url")
        );
        Assertions.assertEquals(
                "",
                resolver.getProperty("payment.issue-alert.receipt-query.gateway-access.access-key")
        );
        Assertions.assertEquals(
                Boolean.FALSE,
                resolver.getProperty("payment.issue-alert.receipt-query.rate-limit.enabled", Boolean.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(60),
                resolver.getProperty("payment.issue-alert.receipt-query.rate-limit.window-seconds", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(100),
                resolver.getProperty(
                        "payment.issue-alert.receipt-query.rate-limit.max-queries-per-channel", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(60),
                resolver.getProperty("payment.issue-alert.receipt-query.compensation.min-age-seconds", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(60),
                resolver.getProperty(
                        "payment.issue-alert.receipt-query.compensation.retry-interval-seconds", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(2),
                resolver.getProperty(
                        "payment.issue-alert.receipt-query.compensation.retry-backoff-factor", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(900),
                resolver.getProperty(
                        "payment.issue-alert.receipt-query.compensation.max-retry-interval-seconds", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(10),
                resolver.getProperty("payment.issue-alert.receipt-query.compensation.max-attempts", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(100),
                resolver.getProperty("payment.issue-alert.receipt-query.compensation.batch-size", Integer.class)
        );
        Assertions.assertEquals(
                Integer.valueOf(30),
                resolver.getProperty(
                        "payment.issue-alert.receipt-query.compensation.claim-timeout-seconds", Integer.class)
        );
    }

    @Test
    void shouldEnableLocalReceiptOnlyInDemoConfiguration() throws IOException {
        PropertySourcesPropertyResolver resolver = load("application-demo.yml");

        Assertions.assertEquals(
                Boolean.TRUE,
                resolver.getProperty("payment.issue-alert.local-receipt-enabled", Boolean.class)
        );
    }

    @Test
    void shouldRejectLocalReceiptOutsideDemoProfileEvenWhenPropertyEnabled() {
        try (AnnotationConfigApplicationContext context = createNotifierContext(null)) {
            context.getBeansOfType(PaymentIssueAlertNotifier.class).values().forEach(notifier ->
                    Assertions.assertEquals("FAILED", notifier.send(buildDispatchItem()).getProviderDeliveryStatus()));
        }
    }

    @Test
    void shouldAllowLocalReceiptWhenDemoProfileAndPropertyEnabled() {
        try (AnnotationConfigApplicationContext context = createNotifierContext("demo")) {
            context.getBeansOfType(PaymentIssueAlertNotifier.class).values().forEach(notifier ->
                    Assertions.assertEquals("ACCEPTED", notifier.send(buildDispatchItem()).getProviderDeliveryStatus()));
        }
    }

    private AnnotationConfigApplicationContext createNotifierContext(String activeProfile) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        if (activeProfile != null) {
            context.getEnvironment().setActiveProfiles(activeProfile);
        }
        Map<String, Object> properties = Collections.<String, Object>singletonMap(
                "payment.issue-alert.local-receipt-enabled", true);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        PropertySourcesPlaceholderConfigurer placeholderConfigurer = new PropertySourcesPlaceholderConfigurer();
        placeholderConfigurer.setEnvironment(context.getEnvironment());
        context.addBeanFactoryPostProcessor(placeholderConfigurer);
        context.register(
                LocalImPaymentIssueAlertNotifier.class,
                LocalSmsPaymentIssueAlertNotifier.class,
                LocalEmailPaymentIssueAlertNotifier.class
        );
        context.refresh();
        return context;
    }

    private PaymentIssueAlertDispatchItemDTO buildDispatchItem() {
        PaymentIssueAlertDispatchItemDTO item = new PaymentIssueAlertDispatchItemDTO();
        item.setIssueNo("ISSUE-CONFIG-001");
        item.setAlertContent("test alert");
        return item;
    }

    private PropertySourcesPropertyResolver load(String resourceName) throws IOException {
        List<PropertySource<?>> loadedSources = new YamlPropertySourceLoader()
                .load(resourceName, new ClassPathResource(resourceName));
        MutablePropertySources propertySources = new MutablePropertySources();
        loadedSources.forEach(propertySources::addLast);
        return new PropertySourcesPropertyResolver(propertySources);
    }
}
