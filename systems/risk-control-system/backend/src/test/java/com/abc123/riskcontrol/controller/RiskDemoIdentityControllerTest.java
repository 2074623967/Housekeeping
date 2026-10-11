package com.abc123.riskcontrol.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

class RiskDemoIdentityControllerTest {

    @Test
    void shouldExposeIdentityOnlyForLocalReviewProfileAndInMemoryH2() {
        try (AnnotationConfigApplicationContext context = context("local-risk-review", "org.h2.Driver")) {
            assertTrue(context.containsBean("riskDemoIdentityController"));
            assertEquals("risk-control-h2-local-review",
                    context.getBean(RiskDemoIdentityController.class).identity().getData());
        }
        try (AnnotationConfigApplicationContext context = context("prod", "org.h2.Driver")) {
            assertFalse(context.containsBean("riskDemoIdentityController"));
        }
        try (AnnotationConfigApplicationContext context = context("local-risk-review", "com.mysql.cj.jdbc.Driver")) {
            assertFalse(context.containsBean("riskDemoIdentityController"));
        }
        for (String url : new String[] {
                "jdbc:h2:tcp://127.0.0.1:9092/risk_control",
                "jdbc:h2:file:/tmp/risk_control",
                "jdbc:h2:mem:risk_control;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA unsafe"}) {
            try (AnnotationConfigApplicationContext context = context("local-risk-review", "org.h2.Driver", url)) {
                assertFalse(context.containsBean("riskDemoIdentityController"));
            }
        }
    }

    private AnnotationConfigApplicationContext context(String profile, String driver) {
        return context(profile, driver,
                "jdbc:h2:mem:risk_control;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    private AnnotationConfigApplicationContext context(String profile, String driver, String url) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        Map<String, Object> datasource = new HashMap<>();
        datasource.put("spring.datasource.driver-class-name", driver);
        datasource.put("spring.datasource.url", url);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("driver",
                datasource));
        context.register(RiskDemoIdentityController.class);
        context.refresh();
        return context;
    }
}
