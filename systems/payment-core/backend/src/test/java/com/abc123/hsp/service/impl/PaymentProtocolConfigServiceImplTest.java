package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentProtocolStatusRequestDTO;
import com.abc123.hsp.entity.PaymentProtocolConfigEntity;
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
        PaymentProtocolStatusRequestDTO request = new PaymentProtocolStatusRequestDTO();
        request.setProtocolCode("PROTO_MEMBER_DEDUCT_V1");
        request.setEnabled(true);
        request.setChangeReason("重新开放会员代扣");
        PaymentProtocolConfigEntity current = new PaymentProtocolConfigEntity();
        current.setProtocolCode("PROTO_MEMBER_DEDUCT_V1");
        current.setStatus("DISABLED");
        current.setStatusType("danger");
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_MEMBER_DEDUCT_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_MEMBER_DEDUCT_V1"))
                .thenReturn(Integer.valueOf(1), Integer.valueOf(1));
        when(paymentConfigMapper.updateProtocolStatus("PROTO_MEMBER_DEDUCT_V1", "ENABLED", "success")).thenReturn(1);
        when(paymentConfigMapper.findChannels()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findRouteRules()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findProtocols()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findReturnCodeMappings()).thenReturn(Collections.emptyList());
        when(paymentConfigMapper.findGateways()).thenReturn(Collections.emptyList());

        new PaymentConfigServiceImpl(paymentConfigMapper).toggleProtocol(request);

        verify(paymentConfigMapper).updateProtocolStatus("PROTO_MEMBER_DEDUCT_V1", "ENABLED", "success");
        verify(paymentConfigMapper).insertProtocolVersion(org.mockito.ArgumentMatchers.argThat(
                version -> "STATUS_CHANGE".equals(version.getChangeType())
                        && "重新开放会员代扣".equals(version.getChangeReason())));
    }
}
