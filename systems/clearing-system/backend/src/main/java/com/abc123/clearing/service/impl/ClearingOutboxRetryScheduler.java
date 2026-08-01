package com.abc123.clearing.service.impl;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Periodically resumes due failed clearing outbox deliveries. */
@Component
public class ClearingOutboxRetryScheduler {

    private final ClearingOutboxRetryService clearingOutboxRetryService;

    public ClearingOutboxRetryScheduler(ClearingOutboxRetryService clearingOutboxRetryService) {
        this.clearingOutboxRetryService = clearingOutboxRetryService;
    }

    @Scheduled(fixedDelayString = "${clearing.outbox-retry.fixed-delay-ms:300000}")
    public void republishDueFailedEvents() {
        clearingOutboxRetryService.republishDueFailedEvents();
    }
}
