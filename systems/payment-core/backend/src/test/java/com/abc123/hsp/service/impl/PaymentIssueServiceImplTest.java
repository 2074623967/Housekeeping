package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentIssueQueryDTO;
import com.abc123.hsp.mapper.PaymentIssueMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付交易异常中心服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentIssueServiceImplTest {

    @Mock
    private PaymentIssueMapper paymentIssueMapper;

    @Test
    void shouldTrimFiltersAndNormalizePaging() {
        when(paymentIssueMapper.findAll(any(PaymentIssueQueryDTO.class))).thenReturn(Collections.emptyList());
        when(paymentIssueMapper.count(any(PaymentIssueQueryDTO.class))).thenReturn(0L);

        PaymentIssueQueryDTO query = new PaymentIssueQueryDTO();
        query.setPaymentOrderId("  PAY-001  ");
        query.setOrderNo("  ORD-001  ");
        query.setIssueType("  待回调未收口  ");
        query.setSeverity("  P1  ");
        query.setChannelCode("  alipay_h5  ");
        query.setPaymentMethod("  支付宝  ");
        query.setPageNo(0);
        query.setPageSize(200);

        new PaymentIssueServiceImpl(paymentIssueMapper).list(query);

        ArgumentCaptor<PaymentIssueQueryDTO> captor = ArgumentCaptor.forClass(PaymentIssueQueryDTO.class);
        verify(paymentIssueMapper).findAll(captor.capture());
        PaymentIssueQueryDTO normalized = captor.getValue();
        assertEquals("PAY-001", normalized.getPaymentOrderId());
        assertEquals("ORD-001", normalized.getOrderNo());
        assertEquals("待回调未收口", normalized.getIssueType());
        assertEquals("P1", normalized.getSeverity());
        assertEquals("alipay_h5", normalized.getChannelCode());
        assertEquals("支付宝", normalized.getPaymentMethod());
        assertEquals(1, normalized.getPageNo());
        assertEquals(100, normalized.getPageSize());
    }
}
