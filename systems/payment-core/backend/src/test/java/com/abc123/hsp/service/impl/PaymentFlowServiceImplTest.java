package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import java.util.Collections;
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
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setBusinessStatus(" WAIT_CALLBACK ");
        query.setKeyword(" 回调 ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(-3);
        query.setPageSize(0);

        org.mockito.Mockito.when(paymentFlowMapper.findAll(query)).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(paymentFlowMapper.count(query)).thenReturn(0L);
        new PaymentFlowServiceImpl(paymentFlowMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals(1, query.getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(1, query.getPageSize());
        org.junit.jupiter.api.Assertions.assertEquals("wx_h5", query.getChannelCode());
        org.junit.jupiter.api.Assertions.assertEquals("H5", query.getTerminal());
        org.junit.jupiter.api.Assertions.assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        org.junit.jupiter.api.Assertions.assertEquals("回调", query.getKeyword());
        org.junit.jupiter.api.Assertions.assertEquals("retryCount", query.getSortField());
        org.junit.jupiter.api.Assertions.assertEquals("asc", query.getSortOrder());
        verify(paymentFlowMapper).findAll(query);
        verify(paymentFlowMapper).count(query);
    }
}
