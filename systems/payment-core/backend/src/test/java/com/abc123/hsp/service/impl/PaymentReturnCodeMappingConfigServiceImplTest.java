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
 * 渠道返回码映射配置服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentReturnCodeMappingConfigServiceImplTest {

    @Mock
    private PaymentConfigMapper paymentConfigMapper;

    @Test
    void shouldToggleReturnCodeMappingStatus() {
        PaymentConfigToggleRequestDTO request = new PaymentConfigToggleRequestDTO();
        request.setConfigCode("offline_bank");
        request.setSubCode("BANK_TIMEOUT");
        request.setEnabled(true);
        when(paymentConfigMapper.updateReturnCodeMappingStatus("offline_bank", "BANK_TIMEOUT", "ENABLED", "success")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleReturnCodeMapping(request);

        verify(paymentConfigMapper).updateReturnCodeMappingStatus("offline_bank", "BANK_TIMEOUT", "ENABLED", "success");
    }
}
