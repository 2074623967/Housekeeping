package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付处理日志分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentLogServiceImplTest {

    @Mock
    private PaymentLogMapper paymentLogMapper;

    @Test
    void shouldNormalizeAndForwardPagingQuery() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setProcessStage("支付提交");
        query.setLogLevel("INFO");
        query.setPageNo(3);
        query.setPageSize(25);

        new PaymentLogServiceImpl(paymentLogMapper).list(query);

        verify(paymentLogMapper).findAll(query);
        verify(paymentLogMapper).count(query);
    }
}
