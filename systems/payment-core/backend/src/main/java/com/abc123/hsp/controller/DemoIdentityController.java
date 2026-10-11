package com.abc123.hsp.controller;

import com.abc123.hsp.common.ApiResponse;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("demo")
@Conditional(DemoIdentityController.LocalInMemoryH2.class)
@RequestMapping("/api/demo")
public class DemoIdentityController {

    @GetMapping("/identity")
    public ApiResponse<String> identity() {
        return ApiResponse.success("payment-core-h2-demo");
    }

    public static class LocalInMemoryH2 implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "org.h2.Driver".equals(context.getEnvironment().getProperty("spring.datasource.driver-class-name"))
                    && "jdbc:h2:mem:payment_core;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE;NON_KEYWORDS=VALUE"
                    .equals(context.getEnvironment().getProperty("spring.datasource.url"));
        }
    }
}
