package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentProtocolStatusReviewRequestDTO;
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
    void shouldSubmitProtocolStatusReview() {
        PaymentProtocolStatusReviewRequestDTO request = new PaymentProtocolStatusReviewRequestDTO();
        request.setProtocolCode("PROTO_MEMBER_DEDUCT_V1");
        request.setEnabled(true);
        request.setRequester("protocol-maker");
        request.setChangeReason("重新开放会员代扣");
        PaymentProtocolConfigEntity current = new PaymentProtocolConfigEntity();
        current.setProtocolCode("PROTO_MEMBER_DEDUCT_V1");
        current.setStatus("DISABLED");
        current.setStatusType("danger");
        when(paymentConfigMapper.findProtocolByCodeForUpdate("PROTO_MEMBER_DEDUCT_V1")).thenReturn(current);
        when(paymentConfigMapper.findLatestProtocolVersionNo("PROTO_MEMBER_DEDUCT_V1"))
                .thenReturn(Integer.valueOf(1), Integer.valueOf(1));
        when(paymentConfigMapper.insertProtocolReview(
                org.mockito.ArgumentMatchers.any())).thenReturn(1);

        new PaymentConfigServiceImpl(paymentConfigMapper).submitProtocolStatusReview(request);

        verify(paymentConfigMapper).insertProtocolReview(org.mockito.ArgumentMatchers.argThat(
                review -> "STATUS_CHANGE".equals(review.getOperationType())
                        && "ENABLED".equals(review.getStatus())
                        && "重新开放会员代扣".equals(review.getRequestReason())));
    }
}
