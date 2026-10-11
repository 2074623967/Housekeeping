package com.abc123.opsconfig.controller;

import com.abc123.opsconfig.common.ApiResponse;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.context.annotation.Profile;
import org.springframework.core.type.AnnotatedTypeMetadata;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@Profile("local-payment-chain")
@Conditional(LocalPaymentChainIdentityController.LocalInMemoryH2.class)
@RequestMapping("/api/demo")
public class LocalPaymentChainIdentityController {

    @GetMapping("/identity")
    public ApiResponse<String> identity() {
        return ApiResponse.success("ops-config-system-h2-local-payment-chain");
    }

    public static class LocalInMemoryH2 implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            return "org.h2.Driver".equals(context.getEnvironment().getProperty("spring.datasource.driver-class-name"))
                    && "jdbc:h2:mem:ops_config;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1"
                    .equals(context.getEnvironment().getProperty("spring.datasource.url"));
        }
    }
}
