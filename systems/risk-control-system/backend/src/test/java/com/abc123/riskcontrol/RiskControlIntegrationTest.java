package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import com.abc123.riskcontrol.service.RiskControlService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * 验证风控基线链路。
 */
@SpringBootTest
class RiskControlIntegrationTest {

    @Autowired
    private RiskControlService service;

    @Test
    void shouldLoadRiskSummaryAndLists() {
        assertEquals(4, service.summary().getMetrics().size());
        assertEquals(3, service.policies().getRecords().size());
        assertEquals(3, service.reviewOrders().getRecords().size());
    }

    @Test
    void shouldApprovePendingReviewOrder() {
        RiskReviewActionRequestDTO request = new RiskReviewActionRequestDTO();
        request.setReviewNo("REVIEW-1001");
        request.setAction("APPROVE");
        assertEquals("APPROVED", service.reviewAction(request).getRecords().get(0).getStatus());
    }

    @Test
    void shouldRejectEmptyToggleCode() {
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setEnabled(true);
        assertThrows(BusinessException.class, () -> service.togglePolicy(request));
    }
}
