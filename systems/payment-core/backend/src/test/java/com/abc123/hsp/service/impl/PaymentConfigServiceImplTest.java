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
import com.abc123.hsp.entity.PaymentIssueDutyRosterEntity;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
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
        when(paymentConfigMapper.updateRouteRuleStatus("RULE_NONE", "ENABLED", "success")).thenReturn(0);

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
        policy.setAllowedMerchantNos("MCH_HOME_APP");
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
        org.junit.jupiter.api.Assertions.assertEquals("MCH_HOME_APP", actualPolicy.getAllowedMerchantNos());
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
        policy.setAllowedMerchantNos("MCH_HOME_APP");
        policy.setTokenAuthRequired("开启");
        policy.setAccessTokenValue("token-housekeeping-app-web");
        when(paymentConfigMapper.findControlPolicyBySourceAppId("housekeeping-app-web")).thenReturn(policy);

        PaymentChannelConfigDTO wxChannel = buildChannel("wx_h5", "微信支付", "MCH_HOME_APP", "ENABLED");
        PaymentChannelConfigDTO aliChannel = buildChannel("alipay_h5", "支付宝", "MCH_HOME_APP", "ENABLED");
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
