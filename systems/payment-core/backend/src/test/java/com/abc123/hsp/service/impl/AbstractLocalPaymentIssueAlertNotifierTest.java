package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class AbstractLocalPaymentIssueAlertNotifierTest {

    @Test
    void shouldBoundExternalHttpTimeout() {
        assertEquals(1, AbstractLocalPaymentIssueAlertNotifier.normalizeHttpTimeoutMs(-1));
        assertEquals(1, AbstractLocalPaymentIssueAlertNotifier.normalizeHttpTimeoutMs(0));
        assertEquals(2000, AbstractLocalPaymentIssueAlertNotifier.normalizeHttpTimeoutMs(2000));
        assertEquals(60000, AbstractLocalPaymentIssueAlertNotifier.normalizeHttpTimeoutMs(60001));
    }
}
