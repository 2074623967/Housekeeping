package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentControlPolicyDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckItemDTO;
import com.abc123.hsp.dto.PaymentControlPolicySelfCheckSummaryDTO;
import com.abc123.hsp.dto.PaymentAlertProviderConfigDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterDTO;
import com.abc123.hsp.dto.PaymentIssueDutyRosterUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConfigDTO;
import com.abc123.hsp.dto.PaymentRouteRuleConflictDTO;
import com.abc123.hsp.dto.PaymentRouteRuleUpsertRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleRollbackRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationDTO;
import com.abc123.hsp.dto.PaymentRouteRuleValidationRequestDTO;
import com.abc123.hsp.dto.PaymentRouteRuleVersionDTO;
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.entity.PaymentRouteRuleConfigEntity;
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
        mockOverviewDependencies();
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        service.createRouteRule(request);

        ArgumentCaptor<PaymentRouteRuleConfigEntity> createCaptor =
                ArgumentCaptor.forClass(PaymentRouteRuleConfigEntity.class);
        verify(paymentConfigMapper).insertRouteRule(createCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("RULE_HOME_APP", createCaptor.getValue().getRuleCode());
        org.junit.jupiter.api.Assertions.assertEquals("ENABLED", createCaptor.getValue().getStatus());

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
        when(paymentConfigMapper.findRouteRules())
                .thenReturn(Arrays.asList(first, divergent, shadowed, potential, invalid, disabled));

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
                        || "RULE_DISABLED".equals(item.getConflictingRuleCode())));
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
    void shouldListAndRollbackRouteRuleVersion() {
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
        mockOverviewDependencies();
        PaymentConfigServiceImpl service = new PaymentConfigServiceImpl(paymentConfigMapper);

        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(1),
                service.listRouteRuleVersions("RULE_HOME_APP").get(0).getVersionNo());
        PaymentRouteRuleRollbackRequestDTO request = new PaymentRouteRuleRollbackRequestDTO();
        request.setVersionNo(Integer.valueOf(1));
        request.setChangeReason("恢复 H5 专用规则");
        service.rollbackRouteRule("RULE_HOME_APP", request);

        verify(paymentConfigMapper).updateRouteRule(org.mockito.ArgumentMatchers.argThat(
                entity -> "terminal=H5".equals(entity.getMatchExpression())));
        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(4).equals(version.getVersionNo())
                        && "ROLLBACK".equals(version.getChangeType())
                        && Integer.valueOf(1).equals(version.getSourceVersionNo())
                        && "恢复 H5 专用规则".equals(version.getChangeReason())));
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

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .rollbackRouteRule("RULE_HOME_APP", request));

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

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper)
                        .rollbackRouteRule("RULE_HOME_APP", request));

        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .updateRouteRule(org.mockito.ArgumentMatchers.any(PaymentRouteRuleConfigEntity.class));
        verify(paymentConfigMapper, org.mockito.Mockito.never())
                .insertRouteRuleVersion(org.mockito.ArgumentMatchers.any(PaymentRouteRuleVersionEntity.class));
    }

    @Test
    void shouldVersionRouteRuleStatusChange() {
        PaymentRouteRuleConfigEntity current = buildRouteRuleEntity();
        when(paymentConfigMapper.findRouteRuleByCodeForUpdate("RULE_HOME_APP")).thenReturn(current);
        when(paymentConfigMapper.findLatestRouteRuleVersionNo("RULE_HOME_APP"))
                .thenReturn(Integer.valueOf(2), Integer.valueOf(2));
        when(paymentConfigMapper.updateRouteRuleStatus("RULE_HOME_APP", "DISABLED", "danger")).thenReturn(1);
        mockOverviewDependencies();
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("RULE_HOME_APP");
        request.setEnabled(false);

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleRouteRule(request);

        verify(paymentConfigMapper).insertRouteRuleVersion(org.mockito.ArgumentMatchers.argThat(
                version -> Integer.valueOf(3).equals(version.getVersionNo())
                        && "STATUS_CHANGE".equals(version.getChangeType())
                        && "DISABLED".equals(version.getStatus())));
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
        when(paymentConfigMapper.findChannels()).thenReturn(Arrays.asList(channel));
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findAlertProviders()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        String csv = new PaymentConfigServiceImpl(paymentConfigMapper).exportGovernanceSnapshotCsv("CHANNELS");

        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("配置域,主键编码"));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"支付渠道\""));
        org.junit.jupiter.api.Assertions.assertTrue(csv.contains("\"wx_h5\""));
        org.junit.jupiter.api.Assertions.assertFalse(csv.contains("\"路由规则\""));
    }

    @Test
    void shouldToggleChannelStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("wx_h5");
        request.setEnabled(false);
        when(paymentConfigMapper.updateChannelStatus("wx_h5", "DISABLED", "danger")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleChannel(request);

        verify(paymentConfigMapper).updateChannelStatus("wx_h5", "DISABLED", "danger");
    }

    @Test
    void shouldToggleIssueDutyRosterStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("DUTY_WAIT_CALLBACK_P1");
        request.setEnabled(false);
        when(paymentConfigMapper.updateIssueDutyRosterStatus("DUTY_WAIT_CALLBACK_P1", "DISABLED", "danger")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleIssueDutyRoster(request);

        verify(paymentConfigMapper).updateIssueDutyRosterStatus("DUTY_WAIT_CALLBACK_P1", "DISABLED", "danger");
    }

    @Test
    void shouldToggleAlertProviderStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("ALERT_IM_WECOM");
        request.setEnabled(false);
        when(paymentConfigMapper.updateAlertProviderStatus("ALERT_IM_WECOM", "DISABLED", "danger")).thenReturn(1);
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleAlertProvider(request);

        verify(paymentConfigMapper).updateAlertProviderStatus("ALERT_IM_WECOM", "DISABLED", "danger");
    }

    @Test
    void shouldCreateIssueDutyRoster() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        when(paymentConfigMapper.findIssueDutyRosterByCode("DUTY_NEW_P1")).thenReturn(null);
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterEntity> entityCaptor = ArgumentCaptor.forClass(PaymentIssueDutyRosterEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRoster(entityCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("待回调未收口", entityCaptor.getValue().getIssueType());
        org.junit.jupiter.api.Assertions.assertEquals("P1", entityCaptor.getValue().getSeverity());
        org.junit.jupiter.api.Assertions.assertEquals("支付后端值班组", entityCaptor.getValue().getResponsibilityGroup());
        org.junit.jupiter.api.Assertions.assertEquals("1,2,3,4,5", entityCaptor.getValue().getWeekdayScope());
        org.junit.jupiter.api.Assertions.assertEquals("WORKDAY_ONLY", entityCaptor.getValue().getHolidayStrategy());
        org.junit.jupiter.api.Assertions.assertEquals("支付技术负责人", entityCaptor.getValue().getEscalationReceiver());
        org.junit.jupiter.api.Assertions.assertEquals("30分钟未确认升级支付技术负责人", entityCaptor.getValue().getEscalationPolicy());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(30), entityCaptor.getValue().getEscalationTimeoutMinutes());
    }

    @Test
    void shouldUpdateIssueDutyRoster() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        PaymentIssueDutyRosterEntity entity = new PaymentIssueDutyRosterEntity();
        entity.setRosterCode("DUTY_NEW_P1");
        when(paymentConfigMapper.findIssueDutyRosterByCode("DUTY_NEW_P1")).thenReturn(entity);
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).updateIssueDutyRoster("DUTY_NEW_P1", request);

        verify(paymentConfigMapper).updateIssueDutyRoster(org.mockito.ArgumentMatchers.any(PaymentIssueDutyRosterEntity.class));
    }

    @Test
    void shouldNormalizeIssueDutyRosterWeekdayScope() {
        PaymentIssueDutyRosterUpsertRequestDTO request = buildIssueDutyRosterRequest();
        request.setWeekdayScope("5,1,3,3");
        when(paymentConfigMapper.findIssueDutyRosterByCode("DUTY_NEW_P1")).thenReturn(null);
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterEntity> entityCaptor = ArgumentCaptor.forClass(PaymentIssueDutyRosterEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRoster(entityCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("5,1,3", entityCaptor.getValue().getWeekdayScope());
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
        when(paymentConfigMapper.findIssueDutyRosterByCode("DUTY_NEW_P1")).thenReturn(null);
        mockOverviewDependencies();

        new PaymentConfigServiceImpl(paymentConfigMapper).createIssueDutyRoster(request);

        ArgumentCaptor<PaymentIssueDutyRosterEntity> entityCaptor = ArgumentCaptor.forClass(PaymentIssueDutyRosterEntity.class);
        verify(paymentConfigMapper).insertIssueDutyRoster(entityCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(22), entityCaptor.getValue().getEffectiveStartHour());
        org.junit.jupiter.api.Assertions.assertEquals(Integer.valueOf(6), entityCaptor.getValue().getEffectiveEndHour());
    }

    @Test
    void shouldRejectMissingRouteRule() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("RULE_NONE");
        request.setEnabled(true);
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).toggleRouteRule(request)
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
    void shouldCreateProtocol() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(null);
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request);

        ArgumentCaptor<PaymentProtocolConfigEntity> entityCaptor = ArgumentCaptor.forClass(PaymentProtocolConfigEntity.class);
        verify(paymentConfigMapper).insertProtocol(entityCaptor.capture());
        org.junit.jupiter.api.Assertions.assertEquals("支付签约协议", entityCaptor.getValue().getProtocolTypeName());
        org.junit.jupiter.api.Assertions.assertTrue(entityCaptor.getValue().getProtocolBody().contains("平台按订单金额发起收款"));
    }

    @Test
    void shouldUpdateProtocol() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        PaymentProtocolConfigEntity entity = new PaymentProtocolConfigEntity();
        entity.setProtocolCode("PROTO_TEST_V1");
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(entity);
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findControlPolicies()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findIssueDutyRosters()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).updateProtocol("PROTO_TEST_V1", request);

        verify(paymentConfigMapper).updateProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldRejectDuplicateProtocolCode() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(new PaymentProtocolConfigEntity());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request)
        );
    }

    @Test
    void shouldRejectProtocolWhenTemplateCodeMissing() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setTemplateCode(" ");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request)
        );
    }

    @Test
    void shouldRejectProtocolWhenBodyMissing() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setProtocolBody(" ");

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request)
        );
    }

    @Test
    void shouldRejectProtocolWhenTypeNotInDictionary() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        request.setProtocolType("UNSUPPORTED_PROTOCOL");
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.<PaymentProtocolTypeOptionDTO>emptyList());

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request)
        );
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
        return request;
    }

    private PaymentRouteRuleUpsertRequestDTO buildRouteRuleRequest() {
        PaymentRouteRuleUpsertRequestDTO request = new PaymentRouteRuleUpsertRequestDTO();
        request.setRuleCode("RULE_HOME_APP");
        request.setRuleName("家政 App 小额路由");
        request.setMatchScene("HOME_CLEAN/APP");
        request.setMatchExpression("terminal IN (H5, APP) AND amount<=500");
        request.setTargetChannelCode("wx_h5");
        request.setFallbackChannelCode("alipay_h5");
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
        rule.setPriority(Integer.valueOf(priority));
        rule.setStatus(status);
        return rule;
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
        return request;
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
