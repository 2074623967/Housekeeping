package com.abc123.refund;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.abc123.refund.controller.LocalPaymentChainIdentityController;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class LocalPaymentChainIdentityControllerTest {

    @Test
    void exposesIdentityOnlyForLocalPaymentChainProfileAndInMemoryH2() throws Exception {
        try (AnnotationConfigApplicationContext context = context("local-payment-chain", "org.h2.Driver")) {
            assertTrue(context.containsBean("localPaymentChainIdentityController"));
            assertEquals("refund-center-h2-local-payment-chain",
                    context.getBean(LocalPaymentChainIdentityController.class).identity().getData());
            MockMvcBuilders.standaloneSetup(context.getBean(LocalPaymentChainIdentityController.class))
                    .build().perform(get("/api/demo/identity"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value("0"))
                    .andExpect(jsonPath("$.data").value("refund-center-h2-local-payment-chain"));
        }
        try (AnnotationConfigApplicationContext context = context("prod", "org.h2.Driver")) {
            assertFalse(context.containsBean("localPaymentChainIdentityController"));
        }
        try (AnnotationConfigApplicationContext context = context("local-payment-chain", "com.mysql.cj.jdbc.Driver")) {
            assertFalse(context.containsBean("localPaymentChainIdentityController"));
        }
        for (String url : new String[] {
                "jdbc:h2:tcp://127.0.0.1:9092/local",
                "jdbc:h2:file:/tmp/local",
                "jdbc:h2:mem:refund_center;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;INIT=CREATE SCHEMA unsafe"}) {
            try (AnnotationConfigApplicationContext context = context("local-payment-chain", "org.h2.Driver", url)) {
                assertFalse(context.containsBean("localPaymentChainIdentityController"));
            }
        }
    }

    private AnnotationConfigApplicationContext context(String profile, String driver) {
        return context(profile, driver, "jdbc:h2:mem:refund_center;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1");
    }

    private AnnotationConfigApplicationContext context(String profile, String driver, String url) {
        AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        context.getEnvironment().setActiveProfiles(profile);
        Map<String, Object> datasource = new HashMap<>();
        datasource.put("spring.datasource.driver-class-name", driver);
        datasource.put("spring.datasource.url", url);
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("driver",
                datasource));
        context.register(LocalPaymentChainIdentityController.class);
        context.refresh();
        return context;
    }
}
