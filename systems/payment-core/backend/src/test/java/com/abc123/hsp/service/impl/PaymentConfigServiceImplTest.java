package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentProtocolUpsertRequestDTO;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
    void shouldToggleChannelStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("wx_h5");
        request.setEnabled(false);
        when(paymentConfigMapper.updateChannelStatus("wx_h5", "DISABLED", "danger")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
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
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).createProtocol(request);

        verify(paymentConfigMapper).insertProtocol(org.mockito.ArgumentMatchers.any(PaymentProtocolConfigEntity.class));
    }

    @Test
    void shouldUpdateProtocol() {
        PaymentProtocolUpsertRequestDTO request = buildProtocolRequest();
        PaymentProtocolConfigEntity entity = new PaymentProtocolConfigEntity();
        entity.setProtocolCode("PROTO_TEST_V1");
        when(paymentConfigMapper.findProtocolByCode("PROTO_TEST_V1")).thenReturn(entity);
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

    private PaymentProtocolUpsertRequestDTO buildProtocolRequest() {
        PaymentProtocolUpsertRequestDTO request = new PaymentProtocolUpsertRequestDTO();
        request.setProtocolCode("PROTO_TEST_V1");
        request.setProtocolName("测试协议");
        request.setProtocolType("PAYMENT_SIGN");
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
        request.setPriority(5);
        request.setEnabled(true);
        return request;
    }
}
