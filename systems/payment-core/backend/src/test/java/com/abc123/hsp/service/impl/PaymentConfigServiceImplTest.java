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
}
