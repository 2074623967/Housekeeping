package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.dto.PaymentGatewayConfigDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付网关接入配置服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentGatewayConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldToggleGatewayStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("GATEWAY_BANK_OFFLINE");
        request.setEnabled(true);
        when(paymentConfigMapper.updateGatewayStatus("GATEWAY_BANK_OFFLINE", "ENABLED", "success")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleGateway(request);

        verify(paymentConfigMapper).updateGatewayStatus("GATEWAY_BANK_OFFLINE", "ENABLED", "success");
    }

    @Test
    void shouldExposeGatewayFormalizationFieldsInOverview() {
        PaymentGatewayConfigDTO gateway = new PaymentGatewayConfigDTO();
        gateway.setGatewayCode("GATEWAY_WX_ACQ");
        gateway.setEnvironmentScope("生产+预发+沙箱");
        gateway.setCertificateAlias("wx-mch-cert-v2026.07");
        gateway.setCertificateStatus("VALID");
        gateway.setCertificateStatusType("success");
        gateway.setReleaseStage("灰度 30%");
        gateway.setGrayStrategy("按商户分组灰度至核心城市订单");
        gateway.setCallbackWhitelist("101.1.0.0/16,101.2.0.0/16");
        gateway.setAdapterRegistry("submit=wechat-h5,query=wechat-query,refund=wechat-refund");
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocolTypeOptions()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Arrays.asList(gateway));

        PaymentGatewayConfigDTO actualGateway = new PaymentConfigServiceImpl(paymentConfigMapper)
                .overview()
                .getGateways()
                .get(0);

        org.junit.jupiter.api.Assertions.assertEquals("生产+预发+沙箱", actualGateway.getEnvironmentScope());
        org.junit.jupiter.api.Assertions.assertEquals("wx-mch-cert-v2026.07", actualGateway.getCertificateAlias());
        org.junit.jupiter.api.Assertions.assertEquals("VALID", actualGateway.getCertificateStatus());
        org.junit.jupiter.api.Assertions.assertEquals("灰度 30%", actualGateway.getReleaseStage());
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getGrayStrategy().contains("核心城市"));
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getCallbackWhitelist().contains("101.1.0.0/16"));
        org.junit.jupiter.api.Assertions.assertTrue(actualGateway.getAdapterRegistry().contains("refund=wechat-refund"));
    }
}
