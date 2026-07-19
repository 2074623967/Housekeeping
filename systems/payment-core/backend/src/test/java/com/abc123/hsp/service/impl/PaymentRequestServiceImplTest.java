package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付请求分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceImplTest {

    @Mock
    private PaymentRequestMapper paymentRequestMapper;

    @Test
    void shouldNormalizeAndForwardPagingQuery() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(" PR-001 ");
        query.setPaymentOrderId(" PAY-001 ");
        query.setRequestStatus("处理中");
        query.setPageNo(2);
        query.setPageSize(50);

        new PaymentRequestServiceImpl(paymentRequestMapper).list(query);

        verify(paymentRequestMapper).findAll(query);
        verify(paymentRequestMapper).count(query);
    }
}
