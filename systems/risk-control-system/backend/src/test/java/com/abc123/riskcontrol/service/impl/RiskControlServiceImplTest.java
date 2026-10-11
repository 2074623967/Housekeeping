package com.abc123.riskcontrol.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.riskcontrol.dao.RiskControlDao;
import com.abc123.riskcontrol.common.BusinessException;
import com.abc123.riskcontrol.dto.RiskDecisionRequestDTO;
import com.abc123.riskcontrol.dto.RiskDecisionResultDTO;
import com.abc123.riskcontrol.dto.RiskOpsConfigSnapshotDTO;
import com.abc123.riskcontrol.dto.RiskOpsSystemControlDTO;
import com.abc123.riskcontrol.entity.BlocklistEntity;
import com.abc123.riskcontrol.entity.InterceptEventEntity;
import com.abc123.riskcontrol.entity.LimitRuleEntity;
import com.abc123.riskcontrol.entity.ReviewOrderEntity;
import com.abc123.riskcontrol.entity.RiskPolicyEntity;
import com.abc123.riskcontrol.service.RiskOpsConfigService;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

class RiskControlServiceImplTest {

    @Test
    void shouldRejectPaymentCoreDecisionWithoutSourceApplication() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskDecisionRequestDTO request = decisionRequest("PAY-SOURCE-MISSING-001");
        request.setSourceAppId(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class))
                        .evaluatePaymentDecision(request));

        assertEquals("来源应用标识不能为空或超过64字符", exception.getMessage());
        verify(dao, never()).findLatestReviewOrderByBusinessNo(any());
    }

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
    void shouldGenerateDistinctReviewAndEventNumbersWithinColumnLimit() {
        RiskControlDao dao = mock(RiskControlDao.class);
        LimitRuleEntity limitRule = new LimitRuleEntity();
        limitRule.setRuleCode("LIMIT_REVIEW");
        limitRule.setRuleName("限额复核");
        limitRule.setLimitValue("100");
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME"))
                .thenReturn(Collections.singletonList(limitRule));
        RiskControlServiceImpl service = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class));

        for (int index = 0; index < 100; index++) {
            assertEquals("REVIEW", service.evaluatePaymentDecision(
                    decisionRequest("PAY-GENERATED-RISK-NO-" + index)).getDecision());
        }

        ArgumentCaptor<ReviewOrderEntity> reviews = ArgumentCaptor.forClass(ReviewOrderEntity.class);
        ArgumentCaptor<InterceptEventEntity> events = ArgumentCaptor.forClass(InterceptEventEntity.class);
        verify(dao, times(100)).insertReviewOrder(reviews.capture());
        verify(dao, times(100)).insertInterceptEvent(events.capture());
        Set<String> reviewNumbers = new HashSet<>();
        Set<String> eventNumbers = new HashSet<>();
        for (int index = 0; index < 100; index++) {
            String reviewNo = reviews.getAllValues().get(index).getReviewNo();
            String eventNo = events.getAllValues().get(index).getEventNo();
            assertTrue(reviewNo.matches("REVIEW-\\d+-[0-9a-f]{32}"));
            assertTrue(eventNo.matches("RISK-EVT-\\d+-[0-9a-f]{32}"));
            assertTrue(reviewNo.length() <= 64 && eventNo.length() <= 64);
            reviewNumbers.add(reviewNo);
            eventNumbers.add(eventNo);
        }
        assertEquals(100, reviewNumbers.size());
        assertEquals(100, eventNumbers.size());
    }

    @Test
    void shouldReuseConcurrentWinningReviewOnlyForMatchingContext() {
        RiskControlDao dao = mock(RiskControlDao.class);
        LimitRuleEntity limitRule = new LimitRuleEntity();
        limitRule.setRuleCode("LIMIT_REVIEW");
        limitRule.setRuleName("限额复核");
        limitRule.setLimitValue("100");
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME"))
                .thenReturn(Collections.singletonList(limitRule));
        when(dao.insertReviewOrder(any(ReviewOrderEntity.class)))
                .thenReturn(1).thenThrow(new DuplicateKeyException("business key already claimed"));
        RiskControlServiceImpl service = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class));
        RiskDecisionRequestDTO request = decisionRequest("PAY-CONCURRENT-REVIEW-001");

        RiskDecisionResultDTO created = service.evaluatePaymentDecision(request);
        ArgumentCaptor<ReviewOrderEntity> reviews = ArgumentCaptor.forClass(ReviewOrderEntity.class);
        verify(dao).insertReviewOrder(reviews.capture());
        ReviewOrderEntity winner = reviews.getValue();
        assertEquals("PENDING", winner.getStatus());
        assertEquals(32, winner.getBusinessKey().length);
        when(dao.findReviewOrderByBusinessKeyForUpdate(any(byte[].class))).thenReturn(winner);

        RiskDecisionResultDTO reused = service.evaluatePaymentDecision(request);
        assertEquals("REVIEW", reused.getDecision());
        assertEquals(created.getReviewNo(), reused.getReviewNo());
        ArgumentCaptor<byte[]> businessKeys = ArgumentCaptor.forClass(byte[].class);
        verify(dao).findReviewOrderByBusinessKeyForUpdate(businessKeys.capture());
        assertArrayEquals(winner.getBusinessKey(), businessKeys.getValue());

        request.setSourceAppId("housekeeping-h5-web");
        assertThrows(BusinessException.class, () -> service.evaluatePaymentDecision(request));
    }

    @Test
    void shouldReuseConcurrentWinningEventOnlyForExactIdentity() {
        RiskControlDao dao = mock(RiskControlDao.class);
        when(dao.findEnabledPoliciesForDecision()).thenReturn(Collections.singletonList(directRejectPolicy()));
        when(dao.insertInterceptEvent(any(InterceptEventEntity.class)))
                .thenReturn(1).thenThrow(new DuplicateKeyException("event key already claimed"));
        RiskControlServiceImpl service = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class));
        RiskDecisionRequestDTO request = decisionRequest("PAY-CONCURRENT-EVENT-001");

        RiskDecisionResultDTO created = service.evaluatePaymentDecision(request);
        ArgumentCaptor<InterceptEventEntity> events = ArgumentCaptor.forClass(InterceptEventEntity.class);
        verify(dao).insertInterceptEvent(events.capture());
        InterceptEventEntity winner = events.getValue();
        assertEquals(32, winner.getEventKey().length);
        when(dao.findInterceptEventByKeyForUpdate(any(byte[].class))).thenReturn(winner);

        RiskDecisionResultDTO reused = service.evaluatePaymentDecision(request);
        assertEquals("REJECT", reused.getDecision());
        assertEquals(created.getEventNo(), reused.getEventNo());
        ArgumentCaptor<byte[]> eventKeys = ArgumentCaptor.forClass(byte[].class);
        verify(dao).findInterceptEventByKeyForUpdate(eventKeys.capture());
        assertArrayEquals(winner.getEventKey(), eventKeys.getValue());

        request.setSourceSystem("other-system");
        assertThrows(DuplicateKeyException.class, () -> service.evaluatePaymentDecision(request));
        request.setSourceSystem("payment-core");
        request.setBusinessNo("PAY-CONCURRENT-EVENT-OTHER");
        assertThrows(DuplicateKeyException.class, () -> service.evaluatePaymentDecision(request));
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
    void shouldPreferLaterHardBlocklistOverEarlierSoftBlocklistOnFirstDecision() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskDecisionRequestDTO request = decisionRequest("PAY-FIRST-HARD-BLOCKLIST-001");
        request.setPayerPhone("13800008888");
        BlocklistEntity softBlocklist = new BlocklistEntity();
        softBlocklist.setBlockCode("BLOCK_PHONE_REVIEW");
        softBlocklist.setSubjectType("PHONE");
        softBlocklist.setSubjectValue(request.getPayerPhone());
        softBlocklist.setActionType("REVIEW");
        BlocklistEntity hardBlocklist = new BlocklistEntity();
        hardBlocklist.setBlockCode("BLOCK_DEVICE_INTERCEPT");
        hardBlocklist.setSubjectType("DEVICE");
        hardBlocklist.setSubjectValue(request.getClientDeviceId());
        hardBlocklist.setActionType("INTERCEPT");
        when(dao.findEnabledBlocklists()).thenReturn(java.util.Arrays.asList(softBlocklist, hardBlocklist));

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class))
                .evaluatePaymentDecision(request);

        assertEquals("INTERCEPT", result.getDecision());
        assertEquals("BLOCK_DEVICE_INTERCEPT", result.getHitCode());
        verify(dao, never()).insertReviewOrder(any(ReviewOrderEntity.class));
    }

    @Test
    void shouldPreferHardPolicyOverSoftBlocklistOnFirstDecision() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskDecisionRequestDTO request = decisionRequest("PAY-FIRST-HARD-POLICY-001");
        request.setPayerPhone("13800008888");
        BlocklistEntity softBlocklist = new BlocklistEntity();
        softBlocklist.setBlockCode("BLOCK_PHONE_REVIEW");
        softBlocklist.setSubjectType("PHONE");
        softBlocklist.setSubjectValue(request.getPayerPhone());
        softBlocklist.setActionType("REVIEW");
        when(dao.findEnabledBlocklists()).thenReturn(Collections.singletonList(softBlocklist));
        when(dao.findEnabledPoliciesForDecision()).thenReturn(Collections.singletonList(directRejectPolicy()));

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class))
                .evaluatePaymentDecision(request);

        assertEquals("REJECT", result.getDecision());
        assertEquals("POLICY_DEVICE_RISK", result.getHitCode());
        verify(dao, never()).insertReviewOrder(any(ReviewOrderEntity.class));
    }

    @Test
    void shouldPreferHardPolicyOverSoftLimitOnFirstDecision() {
        RiskControlDao dao = mock(RiskControlDao.class);
        LimitRuleEntity limitRule = new LimitRuleEntity();
        limitRule.setRuleCode("LIMIT_REVIEW");
        limitRule.setRuleName("限额复核");
        limitRule.setLimitValue("100");
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME"))
                .thenReturn(Collections.singletonList(limitRule));
        when(dao.findEnabledPoliciesForDecision()).thenReturn(Collections.singletonList(directRejectPolicy()));

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class))
                .evaluatePaymentDecision(decisionRequest("PAY-FIRST-HARD-AFTER-LIMIT-001"));

        assertEquals("REJECT", result.getDecision());
        assertEquals("POLICY_DEVICE_RISK", result.getHitCode());
        verify(dao, never()).insertReviewOrder(any(ReviewOrderEntity.class));
    }

    @Test
    void shouldPreferLaterHardPolicyOverOpsAndEarlierSoftPolicy() {
        RiskControlDao dao = mock(RiskControlDao.class);
        RiskOpsConfigService riskOpsConfigService = mock(RiskOpsConfigService.class);
        RiskOpsSystemControlDTO control = new RiskOpsSystemControlDTO();
        control.setControlCode("CONTROL_BIG_AMOUNT_REVIEW");
        control.setControlValue("100");
        RiskOpsConfigSnapshotDTO snapshot = new RiskOpsConfigSnapshotDTO();
        snapshot.setEnabledSystemControls(Collections.singletonList(control));
        when(riskOpsConfigService.loadEffectiveSnapshot("HOME_CLEAN", "PAY_CONSUME", "APP"))
                .thenReturn(snapshot);
        RiskPolicyEntity softPolicy = new RiskPolicyEntity();
        softPolicy.setPolicyCode("POLICY_REALNAME_WARN");
        softPolicy.setHitAction("REVIEW");
        when(dao.findEnabledPoliciesForDecision())
                .thenReturn(java.util.Arrays.asList(softPolicy, directRejectPolicy()));
        RiskDecisionRequestDTO request = decisionRequest("PAY-FIRST-HARD-AFTER-OPS-001");
        request.setAmount(new BigDecimal("2000.00"));
        request.setPayerPhone("13800008888");

        RiskDecisionResultDTO result = new RiskControlServiceImpl(dao, riskOpsConfigService)
                .evaluatePaymentDecision(request);

        assertEquals("REJECT", result.getDecision());
        assertEquals("POLICY_DEVICE_RISK", result.getHitCode());
        verify(dao, never()).insertReviewOrder(any(ReviewOrderEntity.class));
    }

    @Test
    void shouldNotLetExistingReviewBypassNewHardBlocklist() {
        RiskControlDao dao = mock(RiskControlDao.class);
        LimitRuleEntity limitRule = new LimitRuleEntity();
        limitRule.setRuleCode("LIMIT_REVIEW");
        limitRule.setRuleName("限额复核");
        limitRule.setLimitValue("100");
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME"))
                .thenReturn(Collections.singletonList(limitRule));
        RiskControlServiceImpl service = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class));
        RiskDecisionRequestDTO request = decisionRequest("PAY-NEW-BLOCKLIST-AFTER-REVIEW-001");

        assertEquals("REVIEW", service.evaluatePaymentDecision(request).getDecision());
        ArgumentCaptor<ReviewOrderEntity> reviewCaptor = ArgumentCaptor.forClass(ReviewOrderEntity.class);
        verify(dao).insertReviewOrder(reviewCaptor.capture());
        ReviewOrderEntity reviewOrder = reviewCaptor.getValue();
        when(dao.findLatestReviewOrderByBusinessNo(request.getBusinessNo())).thenReturn(reviewOrder);

        BlocklistEntity blocklist = new BlocklistEntity();
        blocklist.setBlockCode("BLOCK_DEVICE_NEW");
        blocklist.setSubjectType("DEVICE");
        blocklist.setSubjectValue(request.getClientDeviceId());
        blocklist.setReason("新启用的设备拒绝名单");
        blocklist.setActionType("REJECT");
        when(dao.findEnabledBlocklists()).thenReturn(Collections.singletonList(blocklist));

        assertEquals("REJECT", service.evaluatePaymentDecision(request).getDecision());
        reviewOrder.setStatus("APPROVED");
        RiskDecisionResultDTO result = service.evaluatePaymentDecision(request);
        assertEquals("REJECT", result.getDecision());
        assertEquals("BLOCK_DEVICE_NEW", result.getHitCode());
    }

    @Test
    void shouldNotLetApprovedReviewBypassNewHardPolicy() {
        RiskControlDao dao = mock(RiskControlDao.class);
        LimitRuleEntity limitRule = new LimitRuleEntity();
        limitRule.setRuleCode("LIMIT_REVIEW");
        limitRule.setRuleName("限额复核");
        limitRule.setLimitValue("100");
        when(dao.findEnabledLimitRulesByScene("PAY_CONSUME"))
                .thenReturn(Collections.singletonList(limitRule));
        RiskControlServiceImpl service = new RiskControlServiceImpl(dao, mock(RiskOpsConfigService.class));
        RiskDecisionRequestDTO request = decisionRequest("PAY-NEW-POLICY-AFTER-REVIEW-001");

        assertEquals("REVIEW", service.evaluatePaymentDecision(request).getDecision());
        ArgumentCaptor<ReviewOrderEntity> reviewCaptor = ArgumentCaptor.forClass(ReviewOrderEntity.class);
        verify(dao).insertReviewOrder(reviewCaptor.capture());
        ReviewOrderEntity reviewOrder = reviewCaptor.getValue();
        reviewOrder.setStatus("APPROVED");
        when(dao.findLatestReviewOrderByBusinessNo(request.getBusinessNo())).thenReturn(reviewOrder);
        when(dao.findEnabledPoliciesForDecision()).thenReturn(Collections.singletonList(directRejectPolicy()));

        RiskDecisionResultDTO result = service.evaluatePaymentDecision(request);
        assertEquals("REJECT", result.getDecision());
        assertEquals("POLICY_DEVICE_RISK", result.getHitCode());
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
        request.setSourceAppId("housekeeping-app-web");
        request.setSceneCode("PAY_CONSUME");
        request.setPayScene("HOME_CLEAN");
        request.setTerminal("APP");
        request.setClientDeviceId("RISK-DEVICE-001");
        request.setAmount(new BigDecimal("168.00"));
        return request;
    }
}
