package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventOverviewDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
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
        PaymentEventListItemDTO item = new PaymentEventListItemDTO();
        item.setEventNo("EVT-001");
        item.setEventPayload("{\"customerName\":\"张女士\",\"phone\":\"13800138000\"}");
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        when(paymentEventMapper.count(query)).thenReturn(1L);

        PageResultDTO<PaymentEventListItemDTO> page = new PaymentEventServiceImpl(paymentEventMapper).list(query);
        PaymentEventListItemDTO result = page.getItems().get(0);
        assertEquals(1, page.getTotal());
        assertTrue(result.getEventPayload().contains("张*"));
        assertTrue(result.getEventPayload().contains("138****8000"));
        assertTrue(item.getEventPayload().contains("13800138000"));
        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("PAYMENT_SUCCESS", query.getEventType());
        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertEquals("accounting-system", query.getDownstreamSystem());
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
    }

    @Test
    void shouldNormalizeAndReturnOverview() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setEventType(" PAYMENT_SUCCESS ");
        query.setPublishStatus(" FAILED_OR_DEAD_LETTER ");
        query.setDownstreamSystem(" accounting-system ");
        query.setEventTopic(" payment.trade ");
        PaymentEventOverviewDTO overview = new PaymentEventOverviewDTO();
        overview.setTotalEventCount(18L);
        overview.setFailedOrDeadLetterCount(5L);
        when(paymentEventMapper.findOverview(query)).thenReturn(overview);

        PaymentEventOverviewDTO result = new PaymentEventServiceImpl(paymentEventMapper).overview(query);

        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("PAYMENT_SUCCESS", query.getEventType());
        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertEquals("accounting-system", query.getDownstreamSystem());
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals(18L, result.getTotalEventCount());
        assertEquals(5L, result.getFailedOrDeadLetterCount());
        assertEquals(0, result.getDueRetryEventCount());
        verify(paymentEventMapper).findOverview(query);
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
    void shouldPropagateUnknownRefundSubmitReviewError() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo(" EVT-UNKNOWN ");
        when(paymentEventDispatchService.republish("EVT-UNKNOWN"))
                .thenThrow(new BusinessException(
                        ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED,
                        "review required"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService)
                        .republish(request, new PaymentEventQueryDTO()));

        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(paymentEventDispatchService).republish("EVT-UNKNOWN");
    }

    @Test
    void shouldExportCsvWithCombinedFailedAndDeadLetterFilter() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPublishStatus(" FAILED_OR_DEAD_LETTER ");
        PaymentEventListItemDTO item = new PaymentEventListItemDTO();
        item.setEventNo("EVT-001");
        item.setPublishStatus("DEAD_LETTER");
        item.setEventPayload("{\"reason\":\"retry exhausted\",\"accessToken\":\"token-raw\"}");
        when(paymentEventMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentEventServiceImpl(paymentEventMapper).exportCsv(query);

        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertTrue(csv.contains("EVT-001"));
        assertTrue(csv.contains("DEAD_LETTER"));
        assertTrue(csv.contains("retry exhausted"));
        assertTrue(csv.contains("[REDACTED]"));
        assertFalse(csv.contains("token-raw"));
        assertTrue(item.getEventPayload().contains("token-raw"));
        verify(paymentEventMapper).findAllForExport(query);
    }
}
