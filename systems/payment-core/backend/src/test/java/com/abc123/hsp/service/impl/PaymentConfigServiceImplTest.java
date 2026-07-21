package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentChannelConfigDTO;
import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolTypeOptionDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
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

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleChannel(request);

        verify(paymentConfigMapper).updateChannelStatus("wx_h5", "DISABLED", "danger");
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
    void shouldCreateProtocol() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(null);
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Arrays.asList(buildProtocolTypeOption()));
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());

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

    private PaymentProtocolTypeOptionDTO buildProtocolTypeOption() {
        PaymentProtocolTypeOptionDTO option = new PaymentProtocolTypeOptionDTO();
        option.setProtocolType("PAYMENT_SIGN");
        option.setProtocolTypeName("支付签约协议");
        option.setDescription("适用于正向支付签约场景");
        return option;
    }
}
