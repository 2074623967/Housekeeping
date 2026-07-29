package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付事件出站服务测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentEventServiceImplTest {

    @Mock
    private PaymentEventMapper paymentEventMapper;

    @Test
    void shouldListPaymentEvents() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setEventTopic(" payment.trade ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(1);
        query.setPageSize(20);
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.count(query)).thenReturn(0L);

        assertEquals(0, new PaymentEventServiceImpl(paymentEventMapper).list(query).getTotal());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
    }

    @Test
    void shouldRejectRepublishWithoutEventNo() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper)
                        .republish(new PaymentEventRepublishRequestDTO(), new PaymentEventQueryDTO())
        );
    }

    @Test
    void shouldRepublishEventAndReloadList() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo("EVT001");
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setEventTopic(" payment.trade ");
        query.setSortField(" nextRetryAt ");
        query.setSortOrder(" ASC ");
        when(paymentEventMapper.markRepublished("EVT001")).thenReturn(1);
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.count(query)).thenReturn(0L);

        new PaymentEventServiceImpl(paymentEventMapper).republish(request, query);

        verify(paymentEventMapper).markRepublished("EVT001");
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals("nextRetryAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
    }
}
