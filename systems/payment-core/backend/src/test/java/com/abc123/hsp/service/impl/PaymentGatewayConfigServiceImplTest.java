package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentConfigToggleRequestDTO;
import com.abc123.hsp.mapper.PaymentConfigMapper;
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
}
