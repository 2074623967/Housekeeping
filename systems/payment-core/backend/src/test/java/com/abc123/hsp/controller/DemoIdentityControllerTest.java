package com.abc123.hsp.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

class DemoIdentityControllerTest {

    @Test
    void shouldExposeIdentityOnlyForDemoProfileAndInMemoryH2() throws Exception {
        try (AnnotationConfigApplicationContext context = context("demo", "org.h2.Driver")) {
            assertTrue(context.containsBean("demoIdentityController"));
            assertEquals("0", context.getBean(DemoIdentityController.class).identity().getCode());
            assertEquals("payment-core-h2-demo", context.getBean(DemoIdentityController.class).identity().getData());
            assertEquals("/api/demo", DemoIdentityController.class.getAnnotation(RequestMapping.class).value()[0]);
            assertEquals("/identity", DemoIdentityController.class.getMethod("identity")
                    .getAnnotation(GetMapping.class).value()[0]);
        }
        try (AnnotationConfigApplicationContext context = context("prod", "org.h2.Driver")) {
            assertFalse(context.containsBean("demoIdentityController"));
        }
        try (AnnotationConfigApplicationContext context = context("demo", "com.mysql.cj.jdbc.Driver")) {
            assertFalse(context.containsBean("demoIdentityController"));
        }
        for (String url : new String[] {
                "jdbc:h2:tcp://127.0.0.1:9092/payment_core",
                "jdbc:h2:file:/tmp/payment_core",
                "jdbc:h2:mem:payment_core;INIT=CREATE SCHEMA unsafe"}) {
            try (AnnotationConfigApplicationContext context = context("demo", "org.h2.Driver", url)) {
                assertFalse(context.containsBean("demoIdentityController"));
            }
        }
    }

    private AnnotationConfigApplicationContext context(String profile, String driver) {
        return context(profile, driver,
                "jdbc:h2:mem:payment_core;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE");
    }

    private AnnotationConfigApplicationContext context(String profile, String driver, String url) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        Map<String, Object> datasource = new HashMap<>();
        datasource.put("spring.datasource.driver-class-name", driver);
        datasource.put("spring.datasource.url", url);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("driver",
                datasource));
        context.register(DemoIdentityController.class);
        context.refresh();
        return context;
    }
}
