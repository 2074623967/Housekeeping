package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventDispatchService;
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
    @Mock
    private PaymentEventDispatchService paymentEventDispatchService;

    @Test
    void shouldListPaymentEvents() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setEventType(" PAYMENT_SUCCESS ");
        query.setPublishStatus(" FAILED_OR_DEAD_LETTER ");
        query.setDownstreamSystem(" accounting-system ");
        query.setEventTopic(" payment.trade ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(1);
        query.setPageSize(20);
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.count(query)).thenReturn(0L);

        assertEquals(0, new PaymentEventServiceImpl(paymentEventMapper).list(query).getTotal());
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("PAYMENT_SUCCESS", query.getEventType());
        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertEquals("accounting-system", query.getDownstreamSystem());
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
        when(paymentEventDispatchService.republish("EVT001")).thenReturn(true);
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.count(query)).thenReturn(0L);

        new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService).republish(request, query);

        verify(paymentEventDispatchService).republish("EVT001");
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals("nextRetryAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
    }

    @Test
    void shouldRejectRepublishWhenDispatchServiceReturnsFalse() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo(" EVT404 ");

        when(paymentEventDispatchService.republish("EVT404")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService)
                        .republish(request, new PaymentEventQueryDTO())
        );

        assertEquals("支付事件不存在", exception.getMessage());
        verify(paymentEventDispatchService).republish("EVT404");
    }

    @Test
    void shouldExportCsvWithCombinedFailedAndDeadLetterFilter() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPublishStatus(" FAILED_OR_DEAD_LETTER ");
        PaymentEventListItemDTO item = new PaymentEventListItemDTO();
        item.setEventNo("EVT-001");
        item.setPublishStatus("DEAD_LETTER");
        item.setEventPayload("{\"reason\":\"retry exhausted\"}");
        when(paymentEventMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentEventServiceImpl(paymentEventMapper).exportCsv(query);

        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertTrue(csv.contains("EVT-001"));
        assertTrue(csv.contains("DEAD_LETTER"));
        assertTrue(csv.contains("\"{\"\"reason\"\":\"\"retry exhausted\"\"}\""));
        verify(paymentEventMapper).findAllForExport(query);
    }
}
