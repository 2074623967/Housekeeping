package com.abc123.riskcontrol;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;
import com.abc123.riskcontrol.dto.RiskOpsSystemControlDTO;
import com.abc123.riskcontrol.dto.RiskReviewActionRequestDTO;
import com.abc123.riskcontrol.dto.ToggleRequestDTO;
import java.math.BigDecimal;
import java.util.Collections;
import com.abc123.riskcontrol.service.RiskControlService;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;

/**
 * 验证风控基线链路。
 */
@SpringBootTest
class RiskControlIntegrationTest {

    @Autowired
    private RiskControlService service;

    @MockBean
    private RiskOpsConfigService riskOpsConfigService;

    @Test
    void shouldLoadRiskSummaryAndLists() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        assertEquals(4, service.summary().getMetrics().size());
        assertEquals(3, service.policies().getRecords().size());
        org.junit.jupiter.api.Assertions.assertTrue(service.reviewOrders().getRecords().size() >= 3);
    }

    @Test
    void shouldApprovePendingReviewOrder() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        RiskReviewActionRequestDTO request = new RiskReviewActionRequestDTO();
        request.setReviewNo("REVIEW-1001");
        request.setAction("APPROVE");
        assertEquals("APPROVED", service.reviewAction(request).getRecords().get(0).getStatus());
    }

    @Test
    void shouldRejectRepeatedReviewActionOnSameReviewOrder() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-REVIEW-GUARD-001");
        request.setSourceSystem("payment-core");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13800008888");
        request.setAmount(new BigDecimal("188.00"));

        RiskDecisionResultDTO reviewing = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reviewing.getDecision());

        RiskReviewActionRequestDTO approveRequest = new RiskReviewActionRequestDTO();
        approveRequest.setReviewNo(reviewing.getReviewNo());
        approveRequest.setAction("APPROVE");
        service.reviewAction(approveRequest);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.reviewAction(approveRequest));
        assertEquals("仅待审核复核单允许执行审核动作", exception.getMessage());
    }

    @Test
    void shouldRejectEmptyToggleCode() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
        ToggleRequestDTO request = new ToggleRequestDTO();
        request.setEnabled(true);
        assertThrows(BusinessException.class, () -> service.togglePolicy(request));
    }

    @Test
    void shouldCreateReviewDecisionThenPassAfterApproval() {
        when(riskOpsConfigService.loadEffectiveSnapshot(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString())).thenReturn(null);
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

    @Test
    void shouldReviewWhenOpsConfigBigAmountControlExceeded() {
        RiskOpsSystemControlDTO control = new RiskOpsSystemControlDTO();
        control.setControlCode("CONTROL_BIG_AMOUNT_REVIEW");
        control.setControlName("大额支付人工复核");
        control.setControlValue("5000");
        control.setRiskLevel("高");
        RiskOpsConfigSnapshotDTO snapshot = new RiskOpsConfigSnapshotDTO();
        snapshot.setBusinessCode("HOME_CLEAN");
        snapshot.setPayType("PAY_CONSUME");
        snapshot.setTerminalType("APP");
        snapshot.setEnabledSystemControls(Collections.singletonList(control));
        when(riskOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP")).thenReturn(snapshot);

        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo("PAY-RISK-OPS-001");
        request.setSourceSystem("payment-core");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setPaymentMethod("微信支付");
        request.setChannelCode("wx_h5");
        request.setMerchantNo("MCH_HOME_001");
        request.setTerminal("APP");
        request.setClientIp("127.0.0.1");
        request.setPayerPhone("13900001111");
        request.setAmount(new BigDecimal("6888.00"));

        RiskDecisionResultDTO result = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", result.getDecision());
        assertEquals("大额支付人工复核", result.getRiskTag());
    }
}
