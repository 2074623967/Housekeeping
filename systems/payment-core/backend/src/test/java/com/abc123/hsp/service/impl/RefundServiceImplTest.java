package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.mapper.RefundMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 退款单查询条件下推测试。
 */
@ExtendWith(MockitoExtension.class)
class RefundServiceImplTest {

    @Mock
    private RefundMapper refundMapper;

    @Test
    void shouldForwardRefundQueryToMapper() {
        RefundQueryDTO query = new RefundQueryDTO();
        query.setRefundOrderId("REF-001");
        query.setPaymentOrderId("PAY-001");
        query.setRefundStatus("SUCCESS");
        query.setRefundMethod("原路退回");

        new RefundServiceImpl(refundMapper).list(query);

        verify(refundMapper).findAll(query);
    }
}
