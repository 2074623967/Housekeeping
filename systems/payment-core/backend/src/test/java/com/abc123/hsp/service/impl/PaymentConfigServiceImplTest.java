package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentAlertProviderReviewDTO;
import com.abc123.hsp.dto.PaymentAlertProviderStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolVersionDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewActionRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewDTO;
import com.abc123.hsp.dto.PaymentRouteRuleReviewSubmitRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleStatusReviewRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentIssueDutyRosterReviewEntity;
import com.abc123.hsp.entity.PaymentAlertProviderConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentProtocolReviewEntity;
import com.abc123.hsp.entity.PaymentProtocolVersionEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleReviewEntity;
import com.abc123.hsp.entity.PaymentRouteRuleVersionEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/**
 * 支付配置中心服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldCreateAndUpdateValidatedRouteRule() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO target =
                new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO();
        target.setChannelCode("wx_h5");
        com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO fallback =
                new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO();
        fallback.setChannelCode("alipay_h5");
        when(paymentConfigMapper.findChannelForRouting("wx_h5")).thenReturn(target);
        when(paymentConfigMapper.findChannelForRouting("alipay_h5")).thenReturn(fallback);
        when(paymentConfigMapper.findRouteRuleByCode("RULE_HOME_APP")).thenReturn(null);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"))
                .thenReturn(null, Integer.valueOf(1), Integer.valueOf(1));
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.createRouteRule(request);

        ArgumentCaptor<PaymentRouteRuleConfigEntity> createCaptor =
                ArgumentCaptor.forClass(PaymentRouteRuleConfigEntity.class);
        verify(paymentConfigMapper).insertRouteRule(createCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("RULE_HOME_APP", createCaptor.getValue().getRuleCode());
        org.junit.jupiter.api.Assertions.assertEquals("ENABLED", createCaptor.getValue().getStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(30),
                createCaptor.getValue().getRolloutPercentage());
        org.junit.jupiter.api.Assertions.assertEquals("home-app-v1", createCaptor.getValue().getRolloutSalt());

        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP"))
                .thenReturn(createCaptor.getValue());
        service.updateRouteRule("RULE_HOME_APP", request);

        verify(paymentConfigMapper).updateRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> "RULE_HOME_APP".equals(entity.getRuleCode())
                        && "terminal IN (H5, APP) AND amount<=500".equals(entity.getMatchExpression())));
        ArgumentCaptor<PaymentRouteRuleVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(PaymentRouteRuleVersionEntity.class);
        verify(paymentConfigMapper, org.mockito.Mockito.times(2)).insertRouteRuleVersion(versionCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1),
                versionCaptor.getAllValues().get(0).getVersionNo());
        org.junit.jupiter.api.Assertions.assertEquals("CREATE", versionCaptor.getAllValues().get(0).getChangeType());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2),
                versionCaptor.getAllValues().get(1).getVersionNo());
        org.junit.jupiter.api.Assertions.assertEquals("UPDATE", versionCaptor.getAllValues().get(1).getChangeType());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(30),
                versionCaptor.getAllValues().get(1).getRolloutPercentage());
        org.junit.jupiter.api.Assertions.assertEquals("home-app-v1",
                versionCaptor.getAllValues().get(1).getRolloutSalt());
    }

    @Test
    void shouldRejectInvalidRouteRuleRolloutConfiguration() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        request.setRolloutPercentage(Integer.valueOf(101));
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.createRouteRule(request));

        request.setRolloutPercentage(Integer.valueOf(30));
        request.setRolloutSalt(" ");
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.createRouteRule(request));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldRejectInvalidRouteRuleBeforeWrite() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        request.setMatchExpression("amount>not-a-number");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createRouteRule(request));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldRejectDuplicateRouteRuleCode() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO channel =
                new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO();
        when(paymentConfigMapper.findChannelForRouting("wx_h5")).thenReturn(channel);
        when(paymentConfigMapper.findChannelForRouting("alipay_h5")).thenReturn(channel);
        when(paymentConfigMapper.findRouteRuleByCode("RULE_HOME_APP"))
                .thenReturn(new PaymentRouteRuleConfigEntity());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createRouteRule(request));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldRejectConflictingChannelsAndNegativePriority() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        request.setFallbackChannelCode("wx_h5");
        com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO channel =
                new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO();
        when(paymentConfigMapper.findChannelForRouting("wx_h5")).thenReturn(channel);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createRouteRule(request));

        request.setFallbackChannelCode("");
        request.setPriority(-1);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.createRouteRule(request));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldRejectOversizedRouteRuleChangeReason() {
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        request.setChangeReason(String.join("", Collections.nCopies(256, "a")));
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createRouteRule(request));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldValidateAndSimulateRouteRuleExpression() {
        PaymentRouteRuleValidationRequestDTO request = new PaymentRouteRuleValidationRequestDTO();
        request.setMatchExpression("terminal IN (H5, APP) AND amount<=500");
        request.setTerminal("APP");
        request.setAmount(new java.math.BigDecimal("168.00"));

        PaymentRouteRuleValidationDTO result = new PaymentConfigServiceImpl(paymentConfigMapper)
                .validateRouteRule(request);

        org.junit.jupiter.api.Assertions.assertTrue(result.getValid());
        org.junit.jupiter.api.Assertions.assertTrue(result.getMatched());
        org.junit.jupiter.api.Assertions.assertTrue(result.getMessage().contains("样例上下文命中"));

        request.setMatchExpression("unknownField=value");
        PaymentRouteRuleValidationDTO invalidResult = new PaymentConfigServiceImpl(paymentConfigMapper)
                .validateRouteRule(request);
        org.junit.jupiter.api.Assertions.assertFalse(invalidResult.getValid());
        org.junit.jupiter.api.Assertions.assertFalse(invalidResult.getMatched());
    }

    @Test
    void shouldDetectRouteDivergenceShadowingPotentialCollisionAndInvalidExpression() {
        PaymentRouteRuleConfigDTO first = buildConflictRule(
                "RULE_FIRST", "HOME_CLEAN/H5", "terminal = H5", "wx_h5", 1, "ENABLED");
        PaymentRouteRuleConfigDTO divergent = buildConflictRule(
                "RULE_DIVERGENT", "HOME_CLEAN/APP", "TERMINAL=H5", "alipay_h5", 1, "ENABLED");
        PaymentRouteRuleConfigDTO shadowed = buildConflictRule(
                "RULE_SHADOWED", "HOME_CLEAN/H5", "terminal=H5", "offline_bank", 2, "ENABLED");
        PaymentRouteRuleConfigDTO potential = buildConflictRule(
                "RULE_POTENTIAL", "HOME_CLEAN/PC", "amount<=500", "wx_h5", 1, "ENABLED");
        PaymentRouteRuleConfigDTO invalid = buildConflictRule(
                "RULE_INVALID", "ENTERPRISE", "amount>not-a-number", "offline_bank", 3, "ENABLED");
        PaymentRouteRuleConfigDTO disabled = buildConflictRule(
                "RULE_DISABLED", "HOME_CLEAN/H5", "terminal=H5", "alipay_h5", 1, "DISABLED");
        PaymentRouteRuleConfigDTO zeroRollout = buildConflictRule(
                "RULE_ZERO", "HOME_CLEAN/H5", "terminal=H5", "alipay_h5", 1, "ENABLED");
        zeroRollout.setRolloutPercentage(Integer.valueOf(0));
        when(paymentConfigMapper.findRouteRules())
                .thenReturn(Arrays.asList(first, divergent, shadowed, potential, invalid, disabled, zeroRollout));

        List<PaymentRouteRuleConflictDTO> conflicts =
                new PaymentConfigServiceImpl(paymentConfigMapper).listRouteRuleConflicts();

        org.junit.jupiter.api.Assertions.assertTrue(conflicts.stream().anyMatch(
                item -> "ROUTE_DIVERGENCE".equals(item.getConflictType())
                        && "BLOCKER".equals(item.getSeverity())));
        org.junit.jupiter.api.Assertions.assertTrue(conflicts.stream().anyMatch(
                item -> "SHADOWED_RULE".equals(item.getConflictType())
                        && "RULE_SHADOWED".equals(item.getRuleCode())));
        org.junit.jupiter.api.Assertions.assertTrue(conflicts.stream().anyMatch(
                item -> "PARTIAL_SHADOW".equals(item.getConflictType())
                        && "RULE_SHADOWED".equals(item.getRuleCode())));
        org.junit.jupiter.api.Assertions.assertTrue(conflicts.stream().anyMatch(
                item -> "PRIORITY_COLLISION".equals(item.getConflictType())));
        org.junit.jupiter.api.Assertions.assertTrue(conflicts.stream().anyMatch(
                item -> "INVALID_EXPRESSION".equals(item.getConflictType())
                        && "RULE_INVALID".equals(item.getRuleCode())));
        org.junit.jupiter.api.Assertions.assertFalse(conflicts.stream().anyMatch(
                item -> "RULE_DISABLED".equals(item.getRuleCode())
                        || "RULE_DISABLED".equals(item.getConflictingRuleCode())
                        || "RULE_ZERO".equals(item.getRuleCode())
                        || "RULE_ZERO".equals(item.getConflictingRuleCode())));
    }

    @Test
    void shouldDetectDuplicateRuleAndIgnoreDisjointScenes() {
        PaymentRouteRuleConfigDTO first = buildConflictRule(
                "RULE_FIRST", "HOME_CLEAN/H5", "terminal IN (H5, APP)", "wx_h5", 10, "ENABLED");
        PaymentRouteRuleConfigDTO duplicate = buildConflictRule(
                "RULE_DUPLICATE", "HOME_CLEAN/mobile", " TERMINAL in(H5,APP) ", "wx_h5", 10, "ENABLED");
        PaymentRouteRuleConfigDTO disjoint = buildConflictRule(
                "RULE_DISJOINT", "ENTERPRISE/PC", "terminal IN (H5, APP)", "wx_h5", 10, "ENABLED");
        when(paymentConfigMapper.findRouteRules()).thenReturn(Arrays.asList(first, duplicate, disjoint));

        List<PaymentRouteRuleConflictDTO> conflicts =
                new PaymentConfigServiceImpl(paymentConfigMapper).listRouteRuleConflicts();

        org.junit.jupiter.api.Assertions.assertEquals(1, conflicts.size());
        org.junit.jupiter.api.Assertions.assertEquals("DUPLICATE_RULE", conflicts.get(0).getConflictType());
        org.junit.jupiter.api.Assertions.assertEquals("h5 / home_clean", conflicts.get(0).getOverlappingScene());
    }

    @Test
    void shouldDetectOverlappingDuplicateWithoutTreatingScopesAsEqual() {
        PaymentRouteRuleConfigDTO first = buildConflictRule(
                "RULE_FIRST", "HOME_CLEAN/H5", "terminal=H5", "wx_h5", 10, "ENABLED");
        PaymentRouteRuleConfigDTO overlapping = buildConflictRule(
                "RULE_OVERLAPPING", "H5/PC", "TERMINAL = H5", "wx_h5", 10, "ENABLED");
        when(paymentConfigMapper.findRouteRules()).thenReturn(Arrays.asList(first, overlapping));

        List<PaymentRouteRuleConflictDTO> conflicts =
                new PaymentConfigServiceImpl(paymentConfigMapper).listRouteRuleConflicts();

        org.junit.jupiter.api.Assertions.assertEquals(1, conflicts.size());
        org.junit.jupiter.api.Assertions.assertEquals("OVERLAPPING_DUPLICATE", conflicts.get(0).getConflictType());
        org.junit.jupiter.api.Assertions.assertEquals("h5", conflicts.get(0).getOverlappingScene());
    }

    @Test
    void shouldTreatPartialRolloutAsPartialShadowForSameScope() {
        PaymentRouteRuleConfigDTO gray = buildConflictRule(
                "RULE_GRAY", "HOME_CLEAN/H5", "terminal=H5", "wx_h5", 1, "ENABLED");
        gray.setRolloutPercentage(Integer.valueOf(30));
        PaymentRouteRuleConfigDTO later = buildConflictRule(
                "RULE_LATER", "HOME_CLEAN/H5", "terminal=H5", "alipay_h5", 2, "ENABLED");
        when(paymentConfigMapper.findRouteRules()).thenReturn(Arrays.asList(gray, later));

        List<PaymentRouteRuleConflictDTO> conflicts =
                new PaymentConfigServiceImpl(paymentConfigMapper).listRouteRuleConflicts();

        org.junit.jupiter.api.Assertions.assertEquals(1, conflicts.size());
        org.junit.jupiter.api.Assertions.assertEquals("PARTIAL_SHADOW", conflicts.get(0).getConflictType());
        org.junit.jupiter.api.Assertions.assertEquals("RULE_LATER", conflicts.get(0).getRuleCode());
    }

    @Test
    void shouldSubmitRouteRuleReviewWithoutChangingRuntimeRule() {
        PaymentRouteRuleReviewSubmitRequestDTO request = new PaymentRouteRuleReviewSubmitRequestDTO();
        request.setCandidate(buildRouteRuleRequest());
        request.getCandidate().setChangeReason("收窄 App 小额路由");
        request.setRequester("maker-a");
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP")).thenReturn(Integer.valueOf(3));
        when(paymentConfigMapper.countPendingRouteRuleReviews("RULE_HOME_APP")).thenReturn(0);
        when(paymentConfigMapper.findRouteRuleReviews()).thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleReview(request);

        ArgumentCaptor<PaymentRouteRuleReviewEntity> captor =
                ArgumentCaptor.forClass(PaymentRouteRuleReviewEntity.class);
        verify(paymentConfigMapper).insertRouteRuleReview(captor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("UPDATE", captor.getValue().getOperationType());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(3), captor.getValue().getBaseVersionNo());
        org.junit.jupiter.api.Assertions.assertEquals("PENDING_REVIEW", captor.getValue().getReviewStatus());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(30), captor.getValue().getRolloutPercentage());
        org.junit.jupiter.api.Assertions.assertEquals("home-app-v1", captor.getValue().getRolloutSalt());
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldApproveReviewWithDifferentDeclaredReviewerAndPublishVersion() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("配置与风险检查通过");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode())).thenReturn(current);
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode()))
                .thenReturn(Integer.valueOf(3), Integer.valueOf(3));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.<PaymentRouteRuleConfigDTO>emptyList());
        when(paymentConfigMapper.completeRouteRuleReview(
                review.getRequestNo(), "APPROVED", "success", "reviewer-b", "配置与风险检查通过"))
                .thenReturn(1);
        when(paymentConfigMapper.findRouteRuleReviews()).thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveRouteRuleReview(review.getRequestNo(), action);

        verify(paymentConfigMapper).updateRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> review.getRuleCode().equals(entity.getRuleCode())
                        && Integer.valueOf(30).equals(entity.getRolloutPercentage())
                        && "home-app-v1".equals(entity.getRolloutSalt())));
        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "REVIEW_RELEASE".equals(version.getChangeType())
                        && Integer.valueOf(4).equals(version.getVersionNo())));
        verify(paymentConfigMapper).completeRouteRuleReview(
                review.getRequestNo(), "APPROVED", "success", "reviewer-b", "配置与风险检查通过");
    }

    @Test
    void shouldRejectSameDeclaredPersonAndStaleReview() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        PaymentRouteRuleReviewActionRequestDTO samePerson = new PaymentRouteRuleReviewActionRequestDTO();
        samePerson.setReviewer(review.getRequester());
        samePerson.setReviewComment("self approve");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.approveRouteRuleReview(review.getRequestNo(), samePerson));

        PaymentRouteRuleReviewActionRequestDTO reviewer = new PaymentRouteRuleReviewActionRequestDTO();
        reviewer.setReviewer("reviewer-b");
        reviewer.setReviewComment("review");
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode()))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode())).thenReturn(Integer.valueOf(4));

        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> service.approveRouteRuleReview(review.getRequestNo(), reviewer));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never()).completeRouteRuleReview(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectControlCharactersInReviewIdentitiesBeforeWrites() {
        PaymentRouteRuleReviewSubmitRequestDTO submitRequest = new PaymentRouteRuleReviewSubmitRequestDTO();
        submitRequest.setCandidate(buildRouteRuleRequest());
        submitRequest.getCandidate().setChangeReason("控制字符边界检查");
        submitRequest.setRequester("maker-a\n");
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class, () -> service.submitRouteRuleReview(submitRequest));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleReview(org.mockito.ArgumentMatchers.any(PaymentRouteRuleReviewEntity.class));

        PaymentRouteRuleReviewEntity review = buildPendingReview();
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("maker-a\u0085");
        action.setReviewComment("控制字符不可绕过异人复核");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> service.approveRouteRuleReview(review.getRequestNo(), action));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never()).completeRouteRuleReview(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectReviewWithoutChangingRuntimeRule() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("表达式范围过宽");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.completeRouteRuleReview(
                review.getRequestNo(), "REJECTED", "danger", "reviewer-b", "表达式范围过宽"))
                .thenReturn(1);
        when(paymentConfigMapper.findRouteRuleReviews()).thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).rejectRouteRuleReview(review.getRequestNo(), action);

        verify(paymentConfigMapper).completeRouteRuleReview(
                review.getRequestNo(), "REJECTED", "danger", "reviewer-b", "表达式范围过宽");
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldRejectDuplicatePendingRouteRuleReview() {
        PaymentRouteRuleReviewSubmitRequestDTO request = new PaymentRouteRuleReviewSubmitRequestDTO();
        request.setCandidate(buildRouteRuleRequest());
        request.getCandidate().setChangeReason("重复提交检查");
        request.setRequester("maker-a");
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP"))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.countPendingRouteRuleReviews("RULE_HOME_APP")).thenReturn(1);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleReview(request));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleReview(org.mockito.ArgumentMatchers.any(PaymentRouteRuleReviewEntity.class));
    }

    @Test
    void shouldTranslateConcurrentPendingReviewConflict() {
        PaymentRouteRuleReviewSubmitRequestDTO request = new PaymentRouteRuleReviewSubmitRequestDTO();
        request.setCandidate(buildRouteRuleRequest());
        request.getCandidate().setChangeReason("并发提交检查");
        request.setRequester("maker-a");
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP"))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.countPendingRouteRuleReviews("RULE_HOME_APP")).thenReturn(0);
        when(paymentConfigMapper.insertRouteRuleReview(
                org.mockito.ArgumentMatchers.any(PaymentRouteRuleReviewEntity.class)))
                .thenThrow(new DuplicateKeyException("uk_route_rule_review_pending"));

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleReview(request));

        org.junit.jupiter.api.Assertions.assertEquals("该路由规则已有待复核申请", exception.getMessage());
    }

    @Test
    void shouldApproveCreateReviewAndPublishNewRouteRule() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        review.setOperationType("CREATE");
        review.setBaseVersionNo(null);
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("新规则配置检查通过");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode())).thenReturn(null);
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.<PaymentRouteRuleConfigDTO>emptyList());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode())).thenReturn(null);
        when(paymentConfigMapper.completeRouteRuleReview(
                review.getRequestNo(), "APPROVED", "success", "reviewer-b", "新规则配置检查通过"))
                .thenReturn(1);
        when(paymentConfigMapper.findRouteRuleReviews()).thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveRouteRuleReview(review.getRequestNo(), action);

        verify(paymentConfigMapper).insertRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> review.getRuleCode().equals(entity.getRuleCode())));
        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "REVIEW_RELEASE".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getVersionNo())));
    }

    @Test
    void shouldRejectReviewWhenCandidateHasBlockerConflict() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("检查冲突");
        PaymentRouteRuleConfigDTO conflicting = buildConflictRule(
                "RULE_CONFLICTING", "HOME_CLEAN/APP",
                "terminal IN (H5, APP) AND amount<=500", "offline_bank", 10, "ENABLED");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode()))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode())).thenReturn(Integer.valueOf(3));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.singletonList(conflicting));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveRouteRuleReview(review.getRequestNo(), action));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never()).completeRouteRuleReview(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldRejectAlreadyCompletedRouteRuleReview() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        review.setReviewStatus("APPROVED");
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("重复处理");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveRouteRuleReview(review.getRequestNo(), action));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
    }

    @Test
    void shouldListAndSubmitRouteRuleRollbackReviewWithoutRuntimeMutation() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        PaymentRouteRuleVersionEntity target = buildRouteRuleVersionEntity(1, "terminal=H5");
        PaymentRouteRuleVersionDTO history = new PaymentRouteRuleVersionDTO();
        history.setRuleCode("RULE_HOME_APP");
        history.setVersionNo(Integer.valueOf(1));
        when(paymentConfigMapper.findRouteRuleByCode("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findRouteRuleVersions("RULE_HOME_APP"))
                .thenReturn(Collections.singletonList(history));
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"))
                .thenReturn(Integer.valueOf(3), Integer.valueOf(3));
        when(paymentConfigMapper.findRouteRuleVersion("RULE_HOME_APP", Integer.valueOf(1))).thenReturn(target);
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1),
                service.listRouteRuleVersions("RULE_HOME_APP").get(0).getVersionNo());
        PaymentRouteRuleRollbackRequestDTO request = new PaymentRouteRuleRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setChangeReason("恢复 H5 专用规则");
        request.setRequester("maker-a");
        when(paymentConfigMapper.countPendingRouteRuleReviews("RULE_HOME_APP")).thenReturn(0);
        when(paymentConfigMapper.findRouteRuleReviews())
                .thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());
        service.submitRouteRuleRollbackReview("RULE_HOME_APP", request);

        verify(paymentConfigMapper).insertRouteRuleReview(org.mockito.ArgumentMatchers.argThat(
                review -> "ROLLBACK".equals(review.getOperationType())
                        && Integer.valueOf(3).equals(review.getBaseVersionNo())
                        && Integer.valueOf(1).equals(review.getSourceVersionNo())
                        && "terminal=H5".equals(review.getMatchExpression())
                        && "恢复 H5 专用规则".equals(review.getRequestReason())));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldRecordBaselineBeforeFirstRouteRuleUpdate() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        PaymentRouteRuleUpsertRequestDTO request = buildRouteRuleRequest();
        request.setMatchExpression("terminal=APP AND amount<=300");
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"))
                .thenReturn(Integer.valueOf(0), Integer.valueOf(0), Integer.valueOf(1));
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).updateRouteRule("RULE_HOME_APP", request);

        ArgumentCaptor<PaymentRouteRuleVersionEntity> versionCaptor =
                ArgumentCaptor.forClass(PaymentRouteRuleVersionEntity.class);
        verify(paymentConfigMapper, org.mockito.Mockito.times(2)).insertRouteRuleVersion(versionCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("BASELINE",
                versionCaptor.getAllValues().get(0).getChangeType());
        org.junit.jupiter.api.Assertions.assertEquals("terminal IN (H5, APP) AND amount<=500",
                versionCaptor.getAllValues().get(0).getMatchExpression());
        org.junit.jupiter.api.Assertions.assertEquals("UPDATE",
                versionCaptor.getAllValues().get(1).getChangeType());
        org.junit.jupiter.api.Assertions.assertEquals("terminal=APP AND amount<=300",
                versionCaptor.getAllValues().get(1).getMatchExpression());
    }

    @Test
    void shouldRejectMissingRouteRuleRollbackVersionWithoutMutation() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP")).thenReturn(Integer.valueOf(2));
        PaymentRouteRuleRollbackRequestDTO request = new PaymentRouteRuleRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(99));
        request.setRequester("maker-a");
        request.setChangeReason("恢复历史版本");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitRouteRuleRollbackReview("RULE_HOME_APP", request));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldRejectRollbackWhenSnapshotChannelNoLongerExists() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        PaymentRouteRuleVersionEntity target = buildRouteRuleVersionEntity(1, "terminal=H5");
        target.setTargetChannelCode("removed_channel");
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP")).thenReturn(Integer.valueOf(2));
        when(paymentConfigMapper.findRouteRuleVersion("RULE_HOME_APP", Integer.valueOf(1))).thenReturn(target);
        PaymentRouteRuleRollbackRequestDTO request = new PaymentRouteRuleRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setRequester("maker-a");
        request.setChangeReason("恢复历史版本");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitRouteRuleRollbackReview("RULE_HOME_APP", request));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldSubmitRouteRuleStatusReviewWithoutRuntimeMutation() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.countPendingRouteRuleReviews("RULE_HOME_APP")).thenReturn(0);
        when(paymentConfigMapper.findRouteRuleReviews())
                .thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());
        PaymentRouteRuleStatusReviewRequestDTO request = new PaymentRouteRuleStatusReviewRequestDTO();
        request.setRuleCode("RULE_HOME_APP");
        request.setEnabled(false);
        request.setRequester("maker-a");
        request.setChangeReason("停用异常路由");

        new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleStatusReview(request);

        verify(paymentConfigMapper).insertRouteRuleReview(org.mockito.ArgumentMatchers.argThat(
                review -> "STATUS_CHANGE".equals(review.getOperationType())
                        && Integer.valueOf(2).equals(review.getBaseVersionNo())
                        && review.getSourceVersionNo() == null
                        && "DISABLED".equals(review.getStatus())
                        && "停用异常路由".equals(review.getRequestReason())));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldPublishStatusChangeOnlyAfterReviewApproval() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        review.setOperationType("STATUS_CHANGE");
        review.setStatus("DISABLED");
        review.setStatusType("danger");
        review.setRequestReason("停用异常路由");
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("确认停用");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode()))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode()))
                .thenReturn(Integer.valueOf(3), Integer.valueOf(3));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.<PaymentRouteRuleConfigDTO>emptyList());
        when(paymentConfigMapper.completeRouteRuleReview(
                review.getRequestNo(), "APPROVED", "success", "reviewer-b", "确认停用"))
                .thenReturn(1);
        when(paymentConfigMapper.findRouteRuleReviews())
                .thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveRouteRuleReview(review.getRequestNo(), action);

        verify(paymentConfigMapper).updateRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> "DISABLED".equals(entity.getStatus()) && "danger".equals(entity.getStatusType())));
        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "REVIEW_STATUS_CHANGE".equals(version.getChangeType())
                        && Integer.valueOf(4).equals(version.getVersionNo())
                        && "停用异常路由".equals(version.getChangeReason())));
    }

    @Test
    void shouldPublishRollbackSourceVersionOnlyAfterReviewApproval() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        review.setOperationType("ROLLBACK");
        review.setSourceVersionNo(Integer.valueOf(1));
        review.setMatchExpression("terminal=H5");
        review.setRequestReason("恢复 H5 专用规则");
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("确认回滚");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate(review.getRuleCode()))
                .thenReturn(buildRouteRuleEntity());
        when(paymentConfigMapper.findChannelForRouting("wx_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findChannelForRouting("alipay_h5"))
                .thenReturn(new com.abc123.hsp.dto.PaymentChannelRoutingConfigDTO());
        when(paymentConfigMapper.findLatestRouteRuleVersionNo(review.getRuleCode()))
                .thenReturn(Integer.valueOf(3), Integer.valueOf(3));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.<PaymentRouteRuleConfigDTO>emptyList());
        when(paymentConfigMapper.completeRouteRuleReview(
                review.getRequestNo(), "APPROVED", "success", "reviewer-b", "确认回滚"))
                .thenReturn(1);
        when(paymentConfigMapper.findRouteRuleReviews())
                .thenReturn(Collections.<PaymentRouteRuleReviewDTO>emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).approveRouteRuleReview(review.getRequestNo(), action);

        verify(paymentConfigMapper).updateRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> "terminal=H5".equals(entity.getMatchExpression())));
        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "REVIEW_ROLLBACK".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getSourceVersionNo())
                        && Integer.valueOf(4).equals(version.getVersionNo())
                        && "恢复 H5 专用规则".equals(version.getChangeReason())));
    }

    @Test
    void shouldRejectRollbackReviewWithoutSourceVersion() {
        PaymentRouteRuleReviewEntity review = buildPendingReview();
        review.setOperationType("ROLLBACK");
        review.setSourceVersionNo(null);
        PaymentRouteRuleReviewActionRequestDTO action = new PaymentRouteRuleReviewActionRequestDTO();
        action.setReviewer("reviewer-b");
        action.setReviewComment("确认回滚");
        when(paymentConfigMapper.findRouteRuleReviewForUpdate(review.getRequestNo())).thenReturn(review);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveRouteRuleReview(review.getRequestNo(), action));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldRejectNoopRouteRuleStatusReview() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        PaymentRouteRuleStatusReviewRequestDTO request = new PaymentRouteRuleStatusReviewRequestDTO();
        request.setRuleCode("RULE_HOME_APP");
        request.setEnabled(Boolean.TRUE);
        request.setRequester("maker-a");
        request.setChangeReason("重复启用");
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleStatusReview(request));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleReview(org.mockito.ArgumentMatchers.any(PaymentRouteRuleReviewEntity.class));
    }

    @Test
    void shouldExposeChannelFormalizationFieldsInOverview() {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        channel.setChannelCode("wx_h5");
        channel.setMerchantAppId("wx-app-h5-001");
        channel.setCertificateProfile("wx-cert-profile-v2026.07");
        channel.setNotifySignWindow("300s");
        channel.setRefundWindow("180天");
        channel.setRiskControlTag("实名校验+重复支付监控");
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(channel));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        PaymentChannelConfigDTO actualChannel = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getChannels()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("wx-app-h5-001", actualChannel.getMerchantAppId());
        org.junit.jupiter.api.Assertions.assertEquals("wx-cert-profile-v2026.07", actualChannel.getCertificateProfile());
        org.junit.jupiter.api.Assertions.assertEquals("300s", actualChannel.getNotifySignWindow());
        org.junit.jupiter.api.Assertions.assertEquals("180天", actualChannel.getRefundWindow());
        org.junit.jupiter.api.Assertions.assertTrue(actualChannel.getRiskControlTag().contains("实名校验"));
    }

    @Test
    void shouldExposeIssueDutyRosterInOverview() {
        PaymentIssueDutyRosterDTO roster = new PaymentIssueDutyRosterDTO();
        roster.setRosterCode("DUTY_WAIT_CALLBACK_P1");
        roster.setIssueType("待回调未收口");
        roster.setSeverity("P1");
        roster.setResponsibilityGroup("支付后端值班组");
        roster.setReceiver("支付后端值班");
        roster.setEffectiveStartHour(0);
        roster.setEffectiveEndHour(23);
        roster.setWeekdayScope("1,2,3,4,5");
        roster.setHolidayStrategy("WORKDAY_ONLY");
        roster.setApplicabilityDesc("仅工作日");
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Arrays.asList(roster));

        PaymentIssueDutyRosterDTO actualRoster = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getIssueDutyRosters()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("DUTY_WAIT_CALLBACK_P1", actualRoster.getRosterCode());
        org.junit.jupiter.api.Assertions.assertEquals("待回调未收口", actualRoster.getIssueType());
        org.junit.jupiter.api.Assertions.assertEquals("支付后端值班", actualRoster.getReceiver());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(0), actualRoster.getEffectiveStartHour());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(23), actualRoster.getEffectiveEndHour());
        org.junit.jupiter.api.Assertions.assertEquals("1,2,3,4,5", actualRoster.getWeekdayScope());
        org.junit.jupiter.api.Assertions.assertEquals("WORKDAY_ONLY", actualRoster.getHolidayStrategy());
        org.junit.jupiter.api.Assertions.assertEquals("仅工作日", actualRoster.getApplicabilityDesc());
    }

    @Test
    void shouldExposeAlertProviderInOverview() {
        PaymentAlertProviderConfigDTO provider = new PaymentAlertProviderConfigDTO();
        provider.setProviderCode("ALERT_IM_WECOM");
        provider.setChannelCode("IM");
        provider.setTemplateCode("TPL_PAYMENT_ISSUE_IM_V1");
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findAlertProviders()).thenReturn(Arrays.asList(provider));
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        PaymentAlertProviderConfigDTO actualProvider = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getAlertProviders()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("ALERT_IM_WECOM", actualProvider.getProviderCode());
        org.junit.jupiter.api.Assertions.assertEquals("IM", actualProvider.getChannelCode());
        org.junit.jupiter.api.Assertions.assertEquals("TPL_PAYMENT_ISSUE_IM_V1", actualProvider.getTemplateCode());
    }

    @Test
    void shouldExportGovernanceSnapshotCsv() {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        channel.setChannelCode("wx_h5");
        channel.setMerchantNo("MCH_HOME_APP");
        channel.setChannelName("微信H5支付");
        channel.setPaymentMethod("微信支付");
        channel.setDailyLimit("¥500000.00");
        channel.setSceneScope("家政服务/充值");
        channel.setRiskControlTag("实名校验");
        channel.setStatus("ENABLED");
        channel.setUpdatedAt("2026-08-03 10:00:00");
        when(paymentConfigMapper.findChannelsForExport()).thenReturn(Arrays.asList(channel));

        String csv = new PaymentConfigServiceImpl(paymentConfigMapper).exportGovernanceSnapshotCsv("CHANNELS");

        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("配置域,主键编码"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"支付渠道\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"wx_h5\""));
        org.junit.jupiter.api.Assertions.assertFalse(csv.contains("\"路由规则\""));
        verify(paymentConfigMapper).findChannelsForExport();
        verifyNoMoreInteractions(paymentConfigMapper);
    }

    @Test
    void shouldExportAllGovernanceSectionsWithoutQueryingProtocolOptions() {
        new PaymentConfigServiceImpl(paymentConfigMapper).exportGovernanceSnapshotCsv("ALL");

        verify(paymentConfigMapper).findChannelsForExport();
        verify(paymentConfigMapper).findRouteRulesForExport();
        verify(paymentConfigMapper).findProtocolsForExport();
        verify(paymentConfigMapper).findReturnCodeMappingsForExport();
        verify(paymentConfigMapper).findGatewaysForExport();
        verify(paymentConfigMapper).findControlPoliciesForExport();
        verify(paymentConfigMapper).findAlertProvidersForExport();
        verify(paymentConfigMapper).findIssueDutyRostersForExport();
        verifyNoMoreInteractions(paymentConfigMapper);
    }

    @Test
    void shouldRejectGovernanceSectionExportAboveLimit() {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        when(paymentConfigMapper.findChannelsForExport()).thenReturn(Collections.nCopies(1001, channel));

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .exportGovernanceSnapshotCsv("CHANNELS"));

        org.junit.jupiter.api.Assertions.assertTrue(exception.getMessage().contains("超过1000条"));
        verify(paymentConfigMapper).findChannelsForExport();
        verifyNoMoreInteractions(paymentConfigMapper);
    }

    @Test
    void shouldToggleIssueDutyRosterStatus() {
        PaymentIssueDutyRosterStatusReviewRequestDTO request =
                new PaymentIssueDutyRosterStatusReviewRequestDTO();
        request.setRosterCode("duty_wait_callback_p1");
        request.setEnabled(false);
        request.setRequester("maker-a");
        request.setChangeReason("暂停该值班路由");
        PaymentIssueDutyRosterEntity current = buildIssueDutyRosterEntity("ENABLED");
        current.setRosterCode("DUTY_WAIT_CALLBACK_P1");
        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_WAIT_CALLBACK_P1"))
                .thenReturn(current);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleIssueDutyRoster(request);

        verify(paymentConfigMapper).insertIssueDutyRosterReview(org.mockito.ArgumentMatchers.argThat(
                review -> "STATUS_CHANGE".equals(review.getOperationType())
                        && "DUTY_WAIT_CALLBACK_P1".equals(review.getPendingRosterCode())
                        && "DISABLED".equals(review.getCandidateStatus())
                        && review.getBaseFingerprint().length() == 64));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateIssueDutyRoster(org.mockito.ArgumentMatchers.any(PaymentIssueDutyRosterEntity.class));
    }

    @Test
    void shouldToggleAlertProviderStatus() {
        PaymentAlertProviderStatusReviewRequestDTO request = new PaymentAlertProviderStatusReviewRequestDTO();
        request.setProviderCode("alert_im_wecom");
        request.setEnabled(false);
        request.setRequester("maker-a");
        request.setChangeReason("暂停企业消息派发");
        PaymentAlertProviderConfigEntity current = new PaymentAlertProviderConfigEntity();
        current.setProviderCode("ALERT_IM_WECOM");
        current.setProviderName("企业消息");
        current.setChannelCode("IM");
        current.setEndpointAlias("wecom-alert");
        current.setTemplateCode("PAYMENT_ALERT");
        current.setTemplateBody("异常：${issueNo}");
        current.setRouteRule("P1/P2");
        current.setRoutePriority(Integer.valueOf(10));
        current.setRetryPolicy("最多3次");
        current.setRateLimitPolicy("每分钟100条");
        current.setStatus("ENABLED");
        current.setStatusType("success");
        when(paymentConfigMapper.findAlertProviderByCodeForUpdate("ALERT_IM_WECOM")).thenReturn(current);
        when(paymentConfigMapper.findAlertProviderReviews())
                .thenReturn(Collections.singletonList(new PaymentAlertProviderReviewDTO()));

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleAlertProvider(request);

        verify(paymentConfigMapper).insertAlertProviderReview(org.mockito.ArgumentMatchers.argThat(
                review -> "STATUS_CHANGE".equals(review.getOperationType())
                        && "ALERT_IM_WECOM".equals(review.getPendingProviderCode())
                        && "DISABLED".equals(review.getCandidateStatus())
                        && review.getBaseFingerprint().length() == 64));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateAlertProvider(org.mockito.ArgumentMatchers.any(PaymentAlertProviderConfigEntity.class));
    }

    @Test
    void shouldCreateIssueDutyRoster() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentIssueDutyRosterReviewEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRosterReview(reviewCaptor.capture());
        PaymentIssueDutyRosterReviewEntity review = reviewCaptor.getValue();
        org.junit.jupiter.api.Assertions.assertEquals("CREATE", review.getOperationType());
        org.junit.jupiter.api.Assertions.assertEquals("待回调未收口", review.getIssueType());
        org.junit.jupiter.api.Assertions.assertEquals("P1", review.getSeverity());
        org.junit.jupiter.api.Assertions.assertEquals("支付后端值班组", review.getResponsibilityGroup());
        org.junit.jupiter.api.Assertions.assertEquals("1,2,3,4,5", review.getWeekdayScope());
        org.junit.jupiter.api.Assertions.assertEquals("WORKDAY_ONLY", review.getHolidayStrategy());
        org.junit.jupiter.api.Assertions.assertEquals("支付技术负责人", review.getEscalationReceiver());
        org.junit.jupiter.api.Assertions.assertEquals("30分钟未确认升级支付技术负责人", review.getEscalationPolicy());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(30), review.getEscalationTimeoutMinutes());
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertIssueDutyRoster(org.mockito.ArgumentMatchers.any(PaymentIssueDutyRosterEntity.class));
    }

    @Test
    void shouldUpdateIssueDutyRoster() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        PaymentIssueDutyRosterEntity entity = buildIssueDutyRosterEntity("ENABLED");
        when(paymentConfigMapper.findIssueDutyRosterByCodeForUpdate("DUTY_NEW_P1")).thenReturn(entity);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).updateIssueDutyRoster("DUTY_NEW_P1", request);

        verify(paymentConfigMapper).insertIssueDutyRosterReview(org.mockito.ArgumentMatchers.argThat(
                review -> "UPDATE".equals(review.getOperationType())
                        && review.getBaseFingerprint().length() == 64));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateIssueDutyRoster(org.mockito.ArgumentMatchers.any(PaymentIssueDutyRosterEntity.class));
    }

    @Test
    void shouldNormalizeIssueDutyRosterWeekdayScope() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        request.setWeekdayScope("5,1,3,3");
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentIssueDutyRosterReviewEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRosterReview(reviewCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("5,1,3", reviewCaptor.getValue().getWeekdayScope());
    }

    @Test
    void shouldRejectInvalidIssueDutyRosterHolidayStrategy() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        request.setHolidayStrategy("HOLIDAY_ONLY");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request)
        );
    }

    @Test
    void shouldRejectInvalidIssueDutyRosterEscalationTimeout() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        request.setEscalationTimeoutMinutes(3);

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request)
        );
    }

    @Test
    void shouldAllowCrossDayIssueDutyRosterWindow() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        request.setEffectiveStartHour(22);
        request.setEffectiveEndHour(6);
        when(paymentConfigMapper.findIssueDutyRosterReviews()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterReviewEntity> reviewCaptor =
                ArgumentCaptor.forClass(PaymentIssueDutyRosterReviewEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRosterReview(reviewCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(22), reviewCaptor.getValue().getEffectiveStartHour());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(6), reviewCaptor.getValue().getEffectiveEndHour());
    }

    @Test
    void shouldRejectMissingRouteRule() {
        PaymentRouteRuleStatusReviewRequestDTO request = new PaymentRouteRuleStatusReviewRequestDTO();
        request.setRuleCode("RULE_NONE");
        request.setEnabled(true);
        request.setRequester("maker-a");
        request.setChangeReason("启用规则");
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).submitRouteRuleStatusReview(request)
        );
    }

    @Test
    void shouldExposeControlPolicyFieldsInOverview() {
        PaymentControlPolicyDTO policy = new PaymentControlPolicyDTO();
        policy.setSourceAppId("housekeeping-app-web");
        policy.setMinuteSubmitLimit(40);
        policy.setStrictMode("开启");
        policy.setSelfCheckStatus("PASS");
        policy.setAllowedPaymentMethods("微信支付,支付宝");
        policy.setAllowedMerchantNos("MCH_WX_10001,MCH_ALI_20001");
        policy.setTokenAuthRequired("开启");
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Arrays.asList(policy));
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        PaymentControlPolicyDTO actualPolicy = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getControlPolicies()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("housekeeping-app-web", actualPolicy.getSourceAppId());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(40), actualPolicy.getMinuteSubmitLimit());
        org.junit.jupiter.api.Assertions.assertEquals("开启", actualPolicy.getStrictMode());
        org.junit.jupiter.api.Assertions.assertTrue(actualPolicy.getAllowedPaymentMethods().contains("微信支付"));
        org.junit.jupiter.api.Assertions.assertEquals("MCH_WX_10001,MCH_ALI_20001", actualPolicy.getAllowedMerchantNos());
        org.junit.jupiter.api.Assertions.assertEquals("开启", actualPolicy.getTokenAuthRequired());
    }

    @Test
    void shouldRunControlPolicySelfCheckAsPassWhenChannelAndGatewayReady() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("housekeeping-app-web");
        PaymentControlPolicyDTO policy = new PaymentControlPolicyDTO();
        policy.setSourceAppId("housekeeping-app-web");
        policy.setAllowedPaymentMethods("微信支付,支付宝");
        policy.setAllowedChannelCodes("wx_h5,alipay_h5");
        policy.setAllowedMerchantNos("MCH_WX_10001,MCH_ALI_20001");
        policy.setTokenAuthRequired("开启");
        policy.setAccessTokenValue("token-housekeeping-app-web");
        when(paymentConfigMapper.findControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(policy);

        PaymentChannelConfigDTO wxChannel = buildChannel("wx_h5", "微信支付", "MCH_WX_10001", "ENABLED");
        PaymentChannelConfigDTO aliChannel = buildChannel("alipay_h5", "支付宝", "MCH_ALI_20001", "ENABLED");
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(wxChannel, aliChannel));
        PaymentGatewayConfigDTO gateway = buildGateway("wx_h5,alipay_h5", "ENABLED");
        when(paymentConfigMapper.findGateways()).thenReturn(Arrays.asList(gateway));
        when(paymentConfigMapper.updateControlPolicySelfCheck(
                org.mockito.ArgumentMatchers.eq("housekeeping-app-web"),
                org.mockito.ArgumentMatchers.eq("PASS"),
                org.mockito.ArgumentMatchers.eq("success"),
                org.mockito.ArgumentMatchers.contains("通过自检"))).thenReturn(1);
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Arrays.asList(policy));
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).runControlPolicySelfCheck(request);

        verify(paymentConfigMapper).updateControlPolicySelfCheck(
                org.mockito.ArgumentMatchers.eq("housekeeping-app-web"),
                org.mockito.ArgumentMatchers.eq("PASS"),
                org.mockito.ArgumentMatchers.eq("success"),
                org.mockito.ArgumentMatchers.contains("通过自检"));
    }

    @Test
    void shouldRunControlPolicySelfCheckAsWarnWhenGatewayMissing() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("housekeeping-h5-web");
        PaymentControlPolicyDTO policy = new PaymentControlPolicyDTO();
        policy.setSourceAppId("housekeeping-h5-web");
        policy.setAllowedPaymentMethods("微信支付");
        policy.setAllowedChannelCodes("wx_h5");
        policy.setAllowedMerchantNos("MCH_HOME_APP");
        policy.setTokenAuthRequired("关闭");
        when(paymentConfigMapper.findControlPolicyBySourceAppId("housekeeping-h5-web")).thenReturn(policy);

        PaymentChannelConfigDTO wxChannel = buildChannel("wx_h5", "微信支付", "MCH_HOME_APP", "ENABLED");
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(wxChannel));
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.<PaymentGatewayConfigDTO>emptyList());
        when(paymentConfigMapper.updateControlPolicySelfCheck(
                org.mockito.ArgumentMatchers.eq("housekeeping-h5-web"),
                org.mockito.ArgumentMatchers.eq("WARN"),
                org.mockito.ArgumentMatchers.eq("warn"),
                org.mockito.ArgumentMatchers.contains("未找到覆盖授权渠道的启用网关"))).thenReturn(1);
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Arrays.asList(policy));

        new PaymentConfigServiceImpl(paymentConfigMapper).runControlPolicySelfCheck(request);

        verify(paymentConfigMapper).updateControlPolicySelfCheck(
                org.mockito.ArgumentMatchers.eq("housekeeping-h5-web"),
                org.mockito.ArgumentMatchers.eq("WARN"),
                org.mockito.ArgumentMatchers.eq("warn"),
                org.mockito.ArgumentMatchers.contains("未找到覆盖授权渠道的启用网关"));
    }

    @Test
    void shouldRunAllEnabledControlPolicySelfChecks() {
        PaymentControlPolicySelfCheckItemDTO item1 = new PaymentControlPolicySelfCheckItemDTO();
        item1.setSourceAppId("housekeeping-app-web");
        PaymentControlPolicySelfCheckItemDTO item2 = new PaymentControlPolicySelfCheckItemDTO();
        item2.setSourceAppId("housekeeping-h5-web");
        when(paymentConfigMapper.findEnabledControlPolicySelfCheckItems()).thenReturn(Arrays.asList(item1, item2));

        PaymentControlPolicyDTO passPolicy = new PaymentControlPolicyDTO();
        passPolicy.setSourceAppId("housekeeping-app-web");
        passPolicy.setAllowedPaymentMethods("微信支付");
        passPolicy.setAllowedChannelCodes("wx_h5");
        passPolicy.setAllowedMerchantNos("MCH_HOME_APP");
        passPolicy.setTokenAuthRequired("关闭");
        PaymentControlPolicyDTO warnPolicy = new PaymentControlPolicyDTO();
        warnPolicy.setSourceAppId("housekeeping-h5-web");
        warnPolicy.setAllowedPaymentMethods("支付宝");
        warnPolicy.setAllowedChannelCodes("alipay_h5");
        warnPolicy.setAllowedMerchantNos("MCH_HOME_H5");
        warnPolicy.setTokenAuthRequired("开启");
        warnPolicy.setAccessTokenValue("");
        when(paymentConfigMapper.findControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(passPolicy);
        when(paymentConfigMapper.findControlPolicyBySourceAppId("housekeeping-h5-web")).thenReturn(warnPolicy);

        PaymentChannelConfigDTO wxChannel = buildChannel("wx_h5", "微信支付", "MCH_HOME_APP", "ENABLED");
        PaymentChannelConfigDTO aliChannel = buildChannel("alipay_h5", "支付宝", "MCH_HOME_H5", "ENABLED");
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(wxChannel, aliChannel));
        when(paymentConfigMapper.findGateways()).thenReturn(Arrays.asList(buildGateway("wx_h5,alipay_h5", "ENABLED")));

        PaymentControlPolicySelfCheckSummaryDTO summary = new PaymentConfigServiceImpl(paymentConfigMapper)
                .runAllEnabledControlPolicySelfChecks();

        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2), summary.getProcessedCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), summary.getPassCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1), summary.getWarnCount());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(0), summary.getFailCount());
        verify(paymentConfigMapper, org.mockito.Mockito.times(2)).updateControlPolicySelfCheck(
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldSubmitProtocolStatusReviewWithoutChangingRuntime() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolStatusReviewRequestDTO request = new PaymentProtocolStatusReviewRequestDTO();
        request.setProtocolCode("PROTO_TEST_V1");
        request.setEnabled(Boolean.FALSE);
        request.setRequester("protocol-maker");
        request.setChangeReason("停止旧版签约入口");
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));

        new PaymentConfigServiceImpl(paymentConfigMapper).submitProtocolStatusReview(request);

        verify(paymentConfigMapper).insertProtocolReview(org.mockito.ArgumentMatchers.argThat(
                review -> Integer.valueOf(2).equals(review.getBaseVersionNo())
                        && "STATUS_CHANGE".equals(review.getOperationType())
                        && "DISABLED".equals(review.getStatus())
                        && "protocol-maker".equals(review.getRequester())
                        && "停止旧版签约入口".equals(review.getRequestReason())));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldListAndSubmitProtocolRollbackReview() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolVersionEntity target = buildProtocolVersionEntity(1, "v0.9.0");
        PaymentProtocolVersionDTO history = new PaymentProtocolVersionDTO();
        history.setVersionNo(Integer.valueOf(2));
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findProtocolVersions("PROTO_TEST_V1"))
                .thenReturn(Collections.singletonList(history));
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.findProtocolVersion("PROTO_TEST_V1", Integer.valueOf(1))).thenReturn(target);
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(2),
                service.listProtocolVersions("PROTO_TEST_V1").get(0).getVersionNo());
        PaymentProtocolRollbackRequestDTO request = new PaymentProtocolRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setRequester("protocol-maker");
        request.setChangeReason("恢复已核对的协议正文");
        service.submitProtocolRollbackReview("PROTO_TEST_V1", request);

        verify(paymentConfigMapper).insertProtocolReview(org.mockito.ArgumentMatchers.argThat(
                review -> Integer.valueOf(2).equals(review.getBaseVersionNo())
                        && "ROLLBACK".equals(review.getOperationType())
                        && Integer.valueOf(1).equals(review.getSourceVersionNo())
                        && "v0.9.0".equals(review.getTemplateVersion())
                        && "恢复已核对的协议正文".equals(review.getRequestReason())));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldRejectStaleProtocolReviewApproval() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-checker");
        request.setReviewComment("同意发布");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1")).thenReturn(Integer.valueOf(3));

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveProtocolReview("PRV-001", request));

        org.junit.jupiter.api.Assertions.assertEquals("协议已产生新版本，请重新提交复核申请", exception.getMessage());
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldApproveProtocolReviewAndPublishVersion() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-checker");
        request.setReviewComment("已核对协议条款");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.completeProtocolReview(
                "PRV-001", "APPROVED", "success", "protocol-checker", "已核对协议条款"))
                .thenReturn(1);

        new PaymentConfigServiceImpl(paymentConfigMapper).approveProtocolReview("PRV-001", request);

        verify(paymentConfigMapper).updateProtocol(org.mockito.ArgumentMatchers.argThat(
                protocol -> "v1.1.0".equals(protocol.getTemplateVersion())));
        verify(paymentConfigMapper).insertProtocolVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(3).equals(version.getVersionNo())
                        && "REVIEW_RELEASE".equals(version.getChangeType())
                        && "更新协议模板".equals(version.getChangeReason())));
        verify(paymentConfigMapper).completeProtocolReview(
                "PRV-001", "APPROVED", "success", "protocol-checker", "已核对协议条款");
    }

    @Test
    void shouldSubmitNewProtocolReviewWithoutPublishing() {
        PaymentProtocolReviewSubmitRequestDTO request = new PaymentProtocolReviewSubmitRequestDTO();
        request.setRequester("protocol-maker");
        request.setCandidate(buildProtocolRequest());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));

        new PaymentConfigServiceImpl(paymentConfigMapper).submitProtocolReview(request);

        verify(paymentConfigMapper).insertProtocolReview(org.mockito.ArgumentMatchers.argThat(
                review -> review.getBaseVersionNo() == null
                        && "CREATE".equals(review.getOperationType())
                        && "protocol-maker".equals(review.getRequester())
                        && "新增测试协议".equals(review.getRequestReason())));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldApproveNewProtocolReview() {
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        review.setOperationType("CREATE");
        review.setBaseVersionNo(null);
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-checker");
        request.setReviewComment("同意新增");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1")).thenReturn(null);
        when(paymentConfigMapper.completeProtocolReview(
                "PRV-001", "APPROVED", "success", "protocol-checker", "同意新增")).thenReturn(1);

        new PaymentConfigServiceImpl(paymentConfigMapper).approveProtocolReview("PRV-001", request);

        verify(paymentConfigMapper).insertProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
        verify(paymentConfigMapper).insertProtocolVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(1).equals(version.getVersionNo())
                        && "REVIEW_RELEASE".equals(version.getChangeType())));
    }

    @Test
    void shouldRejectProtocolReviewWithoutPublishing() {
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-checker");
        request.setReviewComment("条款需要修订");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);
        when(paymentConfigMapper.completeProtocolReview(
                "PRV-001", "REJECTED", "danger", "protocol-checker", "条款需要修订")).thenReturn(1);

        new PaymentConfigServiceImpl(paymentConfigMapper).rejectProtocolReview("PRV-001", request);

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
        verify(paymentConfigMapper).completeProtocolReview(
                "PRV-001", "REJECTED", "danger", "protocol-checker", "条款需要修订");
    }

    @Test
    void shouldApproveProtocolRollbackReviewWithSourceVersion() {
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        review.setOperationType("ROLLBACK");
        review.setSourceVersionNo(Integer.valueOf(1));
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-checker");
        request.setReviewComment("同意恢复历史条款");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(buildProtocolEntity());
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.completeProtocolReview(
                "PRV-001", "APPROVED", "success", "protocol-checker", "同意恢复历史条款"))
                .thenReturn(1);

        new PaymentConfigServiceImpl(paymentConfigMapper).approveProtocolReview("PRV-001", request);

        verify(paymentConfigMapper).insertProtocolVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(3).equals(version.getVersionNo())
                        && "REVIEW_ROLLBACK".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getSourceVersionNo())));
    }

    @Test
    void shouldRejectSameProtocolReviewer() {
        PaymentProtocolReviewEntity review = buildPendingProtocolReview();
        PaymentProtocolReviewActionRequestDTO request = new PaymentProtocolReviewActionRequestDTO();
        request.setReviewer("protocol-maker");
        request.setReviewComment("自审");
        when(paymentConfigMapper.findProtocolReviewForUpdate("PRV-001")).thenReturn(review);

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .approveProtocolReview("PRV-001", request));

        org.junit.jupiter.api.Assertions.assertEquals("申请人与复核人声明不能相同", exception.getMessage());
    }

    @Test
    void shouldCreateProtocolBaselineBeforeFirstReview() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(null, null, Integer.valueOf(1));

        submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request);

        verify(paymentConfigMapper).insertProtocolVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(1).equals(version.getVersionNo())
                        && "BASELINE".equals(version.getChangeType())));
        verify(paymentConfigMapper).insertProtocolReview(org.mockito.ArgumentMatchers.argThat(
                review -> Integer.valueOf(1).equals(review.getBaseVersionNo())
                        && "UPDATE".equals(review.getOperationType())));
    }

    @Test
    void shouldRejectProtocolChangeWithoutReason() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setChangeReason(" ");
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request));

        org.junit.jupiter.api.Assertions.assertEquals("协议变更原因不能为空", exception.getMessage());
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldRejectMissingProtocolRollbackVersion() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolRollbackRequestDTO request = new PaymentProtocolRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(99));
        request.setRequester("protocol-maker");
        request.setChangeReason("恢复历史协议");
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_TEST_V1"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.findProtocolVersion("PROTO_TEST_V1", Integer.valueOf(99))).thenReturn(null);

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .submitProtocolRollbackReview("PROTO_TEST_V1", request));

        org.junit.jupiter.api.Assertions.assertEquals("支付协议历史版本不存在", exception.getMessage());
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldRejectDuplicatePendingProtocolReview() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_TEST_V1")).thenReturn(buildProtocolEntity());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.countPendingProtocolReviews("PROTO_TEST_V1")).thenReturn(1);

        IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request)
        );
        org.junit.jupiter.api.Assertions.assertEquals("该支付协议已有待复核申请", exception.getMessage());
    }

    @Test
    void shouldRejectProtocolWhenTemplateCodeMissing() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setTemplateCode(" ");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request)
        );
    }

    @Test
    void shouldRejectProtocolWhenBodyMissing() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setProtocolBody(" ");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request)
        );
    }

    @Test
    void shouldRejectProtocolWhenTypeNotInDictionary() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setProtocolType("UNSUPPORTED_PROTOCOL");
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.<PaymentProtocolTypeOptionDTO>emptyList());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> submitProtocolRequest(new PaymentConfigServiceImpl(paymentConfigMapper), request)
        );
    }

    private void submitProtocolRequest(PaymentConfigServiceImpl service,
                                       PaymentProtocolUpsertRequestDTO candidate) {
        PaymentProtocolReviewSubmitRequestDTO request = new PaymentProtocolReviewSubmitRequestDTO();
        request.setRequester("protocol-maker");
        request.setCandidate(candidate);
        service.submitProtocolReview(request);
    }

    private PaymentProtocolUpsertRequestDTO buildProtocolRequest() {
        PaymentProtocolUpsertRequestDTO request = new PaymentProtocolUpsertRequestDTO();
        request.setProtocolCode("PROTO_TEST_V1");
        request.setProtocolName("测试协议");
        request.setProtocolType("PAYMENT_SIGN");
        request.setProtocolTypeName("支付签约协议");
        request.setTemplateCode("TPL_TEST_V1");
        request.setTemplateName("测试协议模板");
        request.setTemplateVersion("v1.0.0");
        request.setSignMode("线上签约");
        request.setSignElementSpec("姓名/身份证/手机号");
        request.setESignatureProvider("E-SIGN-CLOUD");
        request.setSceneScope("家政服务");
        request.setChannelScope("wx_h5,alipay_h5");
        request.setMerchantAckRequired("需要");
        request.setRiskControlTag("实名校验");
        request.setProtocolBody("1. 用户授权平台按订单金额发起收款。\n2. 平台提供账单与服务记录。");
        request.setPriority(5);
        request.setEnabled(true);
        request.setChangeReason("新增测试协议");
        return request;
    }

    private PaymentProtocolConfigEntity buildProtocolEntity() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        PaymentProtocolConfigEntity entity = new PaymentProtocolConfigEntity();
        entity.setProtocolCode(request.getProtocolCode());
        entity.setProtocolName(request.getProtocolName());
        entity.setProtocolType(request.getProtocolType());
        entity.setProtocolTypeName(request.getProtocolTypeName());
        entity.setTemplateCode(request.getTemplateCode());
        entity.setTemplateName(request.getTemplateName());
        entity.setTemplateVersion(request.getTemplateVersion());
        entity.setSignMode(request.getSignMode());
        entity.setSignElementSpec(request.getSignElementSpec());
        entity.setESignatureProvider(request.getESignatureProvider());
        entity.setSceneScope(request.getSceneScope());
        entity.setChannelScope(request.getChannelScope());
        entity.setMerchantAckRequired(request.getMerchantAckRequired());
        entity.setRiskControlTag(request.getRiskControlTag());
        entity.setProtocolBody(request.getProtocolBody());
        entity.setPriority(request.getPriority());
        entity.setStatus("ENABLED");
        entity.setStatusType("success");
        return entity;
    }

    private PaymentProtocolReviewEntity buildPendingProtocolReview() {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolReviewEntity review = new PaymentProtocolReviewEntity();
        review.setRequestNo("PRV-001");
        review.setProtocolCode(current.getProtocolCode());
        review.setBaseVersionNo(Integer.valueOf(2));
        review.setOperationType("UPDATE");
        review.setProtocolName(current.getProtocolName());
        review.setProtocolType(current.getProtocolType());
        review.setProtocolTypeName(current.getProtocolTypeName());
        review.setTemplateCode(current.getTemplateCode());
        review.setTemplateName(current.getTemplateName());
        review.setTemplateVersion("v1.1.0");
        review.setSignMode(current.getSignMode());
        review.setSignElementSpec(current.getSignElementSpec());
        review.setESignatureProvider(current.getESignatureProvider());
        review.setSceneScope(current.getSceneScope());
        review.setChannelScope(current.getChannelScope());
        review.setMerchantAckRequired(current.getMerchantAckRequired());
        review.setRiskControlTag(current.getRiskControlTag());
        review.setProtocolBody(current.getProtocolBody());
        review.setStatus(current.getStatus());
        review.setStatusType(current.getStatusType());
        review.setPriority(current.getPriority());
        review.setRequester("protocol-maker");
        review.setRequestReason("更新协议模板");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentProtocolVersionEntity buildProtocolVersionEntity(int versionNo, String templateVersion) {
        PaymentProtocolConfigEntity current = buildProtocolEntity();
        PaymentProtocolVersionEntity version = new PaymentProtocolVersionEntity();
        version.setProtocolCode(current.getProtocolCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setProtocolName(current.getProtocolName());
        version.setProtocolType(current.getProtocolType());
        version.setProtocolTypeName(current.getProtocolTypeName());
        version.setTemplateCode(current.getTemplateCode());
        version.setTemplateName(current.getTemplateName());
        version.setTemplateVersion(templateVersion);
        version.setSignMode(current.getSignMode());
        version.setSignElementSpec(current.getSignElementSpec());
        version.setESignatureProvider(current.getESignatureProvider());
        version.setSceneScope(current.getSceneScope());
        version.setChannelScope(current.getChannelScope());
        version.setMerchantAckRequired(current.getMerchantAckRequired());
        version.setRiskControlTag(current.getRiskControlTag());
        version.setProtocolBody(current.getProtocolBody());
        version.setStatus(current.getStatus());
        version.setStatusType(current.getStatusType());
        version.setPriority(current.getPriority());
        version.setChangeType("BASELINE");
        version.setChangeReason("初始化协议基线");
        return version;
    }

    private PaymentRouteRuleUpsertRequestDTO buildRouteRuleRequest() {
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode("RULE_HOME_APP");
        request.setRuleName("家政 App 小额路由");
        request.setMatchScene("HOME_CLEAN/APP");
        request.setMatchExpression("terminal IN (H5, APP) AND amount<=500");
        request.setTargetChannelCode("wx_h5");
        request.setFallbackChannelCode("alipay_h5");
        request.setRolloutPercentage(Integer.valueOf(30));
        request.setRolloutSalt("home-app-v1");
        request.setPriority(10);
        request.setEnabled(Boolean.TRUE);
        return request;
    }

    private PaymentRouteRuleConfigEntity buildRouteRuleEntity() {
        PaymentRouteRuleConfigEntity entity = new PaymentRouteRuleConfigEntity();
        entity.setRuleCode("RULE_HOME_APP");
        entity.setRuleName("家政 App 小额路由");
        entity.setMatchScene("HOME_CLEAN/APP");
        entity.setMatchExpression("terminal IN (H5, APP) AND amount<=500");
        entity.setTargetChannelCode("wx_h5");
        entity.setFallbackChannelCode("alipay_h5");
        entity.setRolloutPercentage(Integer.valueOf(30));
        entity.setRolloutSalt("home-app-v1");
        entity.setPriority(Integer.valueOf(10));
        entity.setStatus("ENABLED");
        entity.setStatusType("success");
        return entity;
    }

    private PaymentRouteRuleConfigDTO buildConflictRule(String ruleCode,
                                                        String matchScene,
                                                        String matchExpression,
                                                        String targetChannelCode,
                                                        int priority,
                                                        String status) {
        PaymentRouteRuleConfigDTO rule = new PaymentRouteRuleConfigDTO();
        rule.setRuleCode(ruleCode);
        rule.setRuleName(ruleCode + " 名称");
        rule.setMatchScene(matchScene);
        rule.setMatchExpression(matchExpression);
        rule.setTargetChannelCode(targetChannelCode);
        rule.setFallbackChannelCode("");
        rule.setRolloutPercentage(Integer.valueOf(100));
        rule.setRolloutSalt("conflict-v1");
        rule.setPriority(Integer.valueOf(priority));
        rule.setStatus(status);
        return rule;
    }

    private PaymentRouteRuleReviewEntity buildPendingReview() {
        PaymentRouteRuleReviewEntity review = new PaymentRouteRuleReviewEntity();
        review.setRequestNo("RRV-001");
        review.setRuleCode("RULE_HOME_APP");
        review.setBaseVersionNo(Integer.valueOf(3));
        review.setOperationType("UPDATE");
        review.setRuleName("家政 App 小额路由");
        review.setMatchScene("HOME_CLEAN/APP");
        review.setMatchExpression("terminal IN (H5, APP) AND amount<=500");
        review.setTargetChannelCode("wx_h5");
        review.setFallbackChannelCode("alipay_h5");
        review.setRolloutPercentage(Integer.valueOf(30));
        review.setRolloutSalt("home-app-v1");
        review.setStatus("ENABLED");
        review.setStatusType("success");
        review.setPriority(Integer.valueOf(10));
        review.setRequester("maker-a");
        review.setRequestReason("收窄 App 小额路由");
        review.setReviewStatus("PENDING_REVIEW");
        review.setReviewStatusType("warn");
        return review;
    }

    private PaymentRouteRuleVersionEntity buildRouteRuleVersionEntity(int versionNo, String expression) {
        PaymentRouteRuleConfigEntity rule = buildRouteRuleEntity();
        PaymentRouteRuleVersionEntity version = new PaymentRouteRuleVersionEntity();
        version.setRuleCode(rule.getRuleCode());
        version.setVersionNo(Integer.valueOf(versionNo));
        version.setRuleName(rule.getRuleName());
        version.setMatchScene(rule.getMatchScene());
        version.setMatchExpression(expression);
        version.setTargetChannelCode(rule.getTargetChannelCode());
        version.setFallbackChannelCode(rule.getFallbackChannelCode());
        version.setRolloutPercentage(rule.getRolloutPercentage());
        version.setRolloutSalt(rule.getRolloutSalt());
        version.setPriority(rule.getPriority());
        version.setStatus(rule.getStatus());
        version.setStatusType(rule.getStatusType());
        version.setChangeType("BASELINE");
        version.setChangeReason("初始化规则基线");
        return version;
    }

    private PaymentIssueDutyRosterUpsertRequestDTO buildIssueDutyRosterRequest() {
        PaymentIssueDutyRosterUpsertRequestDTO request = new PaymentIssueDutyRosterUpsertRequestDTO();
        request.setRosterCode("DUTY_NEW_P1");
        request.setIssueType("待回调未收口");
        request.setSeverity("P1");
        request.setResponsibilityGroup("支付后端值班组");
        request.setReceiver("支付后端值班");
        request.setNotifyChannels("IN_APP,IM,SMS");
        request.setEscalationLevel("L1");
        request.setEscalationReceiver("支付技术负责人");
        request.setEscalationPolicy("30分钟未确认升级支付技术负责人");
        request.setEscalationTimeoutMinutes(30);
        request.setScheduleTag("交易链路白班");
        request.setEffectiveStartHour(0);
        request.setEffectiveEndHour(23);
        request.setWeekdayScope("1,2,3,4,5");
        request.setHolidayStrategy("WORKDAY_ONLY");
        request.setEnabled(true);
        request.setRequester("maker-a");
        request.setChangeReason("调整异常告警值班路由");
        return request;
    }

    private PaymentIssueDutyRosterEntity buildIssueDutyRosterEntity(String status) {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        PaymentIssueDutyRosterEntity entity = new PaymentIssueDutyRosterEntity();
        entity.setRosterCode(request.getRosterCode());
        entity.setIssueType(request.getIssueType());
        entity.setSeverity(request.getSeverity());
        entity.setResponsibilityGroup(request.getResponsibilityGroup());
        entity.setReceiver(request.getReceiver());
        entity.setNotifyChannels(request.getNotifyChannels());
        entity.setEscalationLevel(request.getEscalationLevel());
        entity.setEscalationReceiver(request.getEscalationReceiver());
        entity.setEscalationPolicy(request.getEscalationPolicy());
        entity.setEscalationTimeoutMinutes(request.getEscalationTimeoutMinutes());
        entity.setScheduleTag(request.getScheduleTag());
        entity.setEffectiveStartHour(request.getEffectiveStartHour());
        entity.setEffectiveEndHour(request.getEffectiveEndHour());
        entity.setWeekdayScope(request.getWeekdayScope());
        entity.setHolidayStrategy(request.getHolidayStrategy());
        entity.setStatus(status);
        entity.setStatusType("ENABLED".equals(status) ? "success" : "danger");
        return entity;
    }

    private PaymentProtocolTypeOptionDTO buildProtocolTypeOption() {
        PaymentProtocolTypeOptionDTO option = new PaymentProtocolTypeOptionDTO();
        option.setProtocolType("PAYMENT_SIGN");
        option.setProtocolTypeName("支付签约协议");
        option.setDescription("适用于正向支付签约场景");
        return option;
    }

    private PaymentChannelConfigDTO buildChannel(String channelCode, String paymentMethod, String merchantNo, String status) {
        PaymentChannelConfigDTO channel = new PaymentChannelConfigDTO();
        channel.setChannelCode(channelCode);
        channel.setPaymentMethod(paymentMethod);
        channel.setMerchantNo(merchantNo);
        channel.setStatus(status);
        return channel;
    }

    private PaymentGatewayConfigDTO buildGateway(String channelScope, String status) {
        PaymentGatewayConfigDTO gateway = new PaymentGatewayConfigDTO();
        gateway.setChannelScope(channelScope);
        gateway.setStatus(status);
        return gateway;
    }

    private void mockOverviewDependencies() {
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findAlertProviders()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());
    }
}
