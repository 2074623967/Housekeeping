package com.abc123.settlement.service.impl;

import com.abc123.settlement.service.PayoutInspectionService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 可选的出款巡检调度器。
 */
@Component
@ConditionalOnProperty(name = "settlement.payout-inspection.enabled", havingValue = "true")
public class PayoutInspectionScheduler {

    private final PayoutInspectionService payoutInspectionService;

    public PayoutInspectionScheduler(PayoutInspectionService payoutInspectionService) {
        this.payoutInspectionService = payoutInspectionService;
    }

    @Scheduled(fixedDelayString = "${settlement.payout-inspection.fixed-delay-ms:300000}")
    public void inspectPayoutBatches() {
        payoutInspectionService.inspect();
    }
}
