package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付流水分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceImplTest {

    @Mock
    private PaymentFlowMapper paymentFlowMapper;

    @Test
    void shouldNormalizeAndForwardPaymentFlowPagingQuery() {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setFlowType("支付尝试");
        query.setPageNo(3);
        query.setPageSize(30);

        new PaymentFlowServiceImpl(paymentFlowMapper).list(query);

        verify(paymentFlowMapper).findAll(query);
        verify(paymentFlowMapper).count(query);
    }
}
