package com.abc123.riskcontrol.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.entity.BlocklistEntity;
import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import com.abc123.riskcontrol.entity.RiskPolicyEntity;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import java.math.BigDecimal;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class RiskControlServiceImplTest {

    @Test
    void shouldCreateTraceableEventForDirectRejectDecision() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskOpsConfigService riskOpsConfigService = mock(RiskOpsConfigService.class);
        RiskPolicyEntity policy = directRejectPolicy();
        when(dao.findEnabledBlocklists()).thenReturn(Collections.emptyList());
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME")).thenReturn(Collections.emptyList());
        when(dao.findEnabledPoliciesForDecision()).thenReturn(Collections.singletonList(policy));

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, riskOpsConfigService)
                .evaluatePaymentDecision(decisionRequest("PAY-DIRECT-REJECT-001"));

        ArgumentCaptor<InterceptEventEntity> eventCaptor = ArgumentCaptor.forClass(InterceptEventEntity.class);
        verify(dao).insertInterceptEvent(eventCaptor.capture());
        InterceptEventEntity event = eventCaptor.getValue();
        assertEquals("REJECT", result.getDecision());
        assertEquals("danger", result.getDecisionType());
        assertEquals("POLICY_DEVICE_RISK", result.getHitCode());
        assertEquals(event.getEventNo(), result.getEventNo());
        assertTrue(result.getEventNo().startsWith("RISK-EVT-"));
        assertNull(result.getReviewNo());
        assertEquals("PAY-DIRECT-REJECT-001", event.getPaymentOrderId());
        assertEquals("POLICY_DEVICE_RISK", event.getHitPolicy());
        assertEquals("已拒绝", event.getDecisionResult());
        assertEquals("payment-core", event.getSourceSystem());
        assertEquals("命中高危策略，已直接拒绝", result.getMessage());
    }

    @Test
    void shouldDescribeDirectBlocklistRejectAsReject() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskOpsConfigService riskOpsConfigService = mock(RiskOpsConfigService.class);
        BlocklistEntity blocklist = new BlocklistEntity();
        blocklist.setBlockCode("BLOCK_DEVICE_REJECT");
        blocklist.setSubjectType("DEVICE");
        blocklist.setSubjectValue("RISK-DEVICE-001");
        blocklist.setReason("设备拒绝名单");
        blocklist.setActionType("REJECT");
        when(dao.findEnabledBlocklists()).thenReturn(Collections.singletonList(blocklist));

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, riskOpsConfigService)
                .evaluatePaymentDecision(decisionRequest("PAY-BLOCKLIST-REJECT-001"));

        assertEquals("REJECT", result.getDecision());
        assertEquals("BLOCK_DEVICE_REJECT", result.getHitCode());
        assertEquals("命中黑名单，已直接拒绝支付提交", result.getMessage());
    }

    @Test
    void shouldUseReviewNumberForManualRejectWithoutCreatingEvent() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskOpsConfigService riskOpsConfigService = mock(RiskOpsConfigService.class);
        ReviewOrderEntity reviewOrder = new ReviewOrderEntity();
        reviewOrder.setReviewNo("REVIEW-REJECTED-001");
        reviewOrder.setBusinessNo("PAY-MANUAL-REJECT-001");
        reviewOrder.setRiskTag("人工复核拒绝");
        reviewOrder.setStatus("REJECTED");
        when(dao.findLatestReviewOrderByBusinessNo("PAY-MANUAL-REJECT-001")).thenReturn(reviewOrder);

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, riskOpsConfigService)
                .evaluatePaymentDecision(decisionRequest("PAY-MANUAL-REJECT-001"));

        assertEquals("REJECT", result.getDecision());
        assertEquals("danger", result.getDecisionType());
        assertEquals("REVIEW-REJECTED-001", result.getReviewNo());
        assertNull(result.getEventNo());
        verify(dao, never()).insertInterceptEvent(any(InterceptEventEntity.class));
    }

    private RiskPolicyEntity directRejectPolicy() {
        RiskPolicyEntity policy = new RiskPolicyEntity();
        policy.setPolicyCode("POLICY_DEVICE_RISK");
        policy.setPolicyName("异常设备拒绝");
        policy.setHitAction("REJECT");
        policy.setRiskLevel("高");
        return policy;
    }

    private RiskDecisionRequestDTO decisionRequest(String businessNo) {
        RiskDecisionRequestDTO request = new RiskDecisionRequestDTO();
        request.setBusinessNo(businessNo);
        request.setSourceSystem("payment-core");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setTerminal("APP");
        request.setClientDeviceId("RISK-DEVICE-001");
        request.setAmount(new BigDecimal("168.00"));
        return request;
    }
}
