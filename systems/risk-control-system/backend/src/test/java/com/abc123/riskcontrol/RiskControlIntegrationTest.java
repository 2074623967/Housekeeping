package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import java.math.BigDecimal;
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
        org.junit.jupiter.api.Assertions.assertTrue(service.reviewOrders().getRecords().size() >= 3);
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

    @Test
    void shouldCreateReviewDecisionThenPassAfterApproval() {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-DECISION-001");
        request.setSourceSystem("payment-core");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");
        request.setAmount(new BigDecimal("168.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());

        RiskReviewActionRequestDTO approveRequest = new RiskReviewActionRequestDTO();
        approveRequest.setReviewNo(reviewing.getReviewNo());
        approveRequest.setAction("APPROVE");
        service.reviewAction(approveRequest);

        RiskDecisionResultDTO passed = service.evaluatePaymentDecision(request);
        assertEquals("PASS", passed.getDecision());
        assertEquals(reviewing.getReviewNo(), passed.getReviewNo());
    }
}
