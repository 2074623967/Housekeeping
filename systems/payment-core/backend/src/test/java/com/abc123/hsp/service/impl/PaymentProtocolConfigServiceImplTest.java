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
 * 支付协议配置服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentProtocolConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldToggleProtocolStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("PROTO_MEMBER_DEDUCT_V1");
        request.setEnabled(true);
        when(paymentConfigMapper.updateProtocolStatus("PROTO_MEMBER_DEDUCT_V1", "ENABLED", "success")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleProtocol(request);

        verify(paymentConfigMapper).updateProtocolStatus("PROTO_MEMBER_DEDUCT_V1", "ENABLED", "success");
    }
}
