package com.abc123.hsp;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@ConditionalOnProperty(
        name = "payment.scheduling.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentSchedulingConfiguration {
}
