package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventDetailDTO;
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
    void shouldNormalizeAndReturnMaskedPaymentEventDetail() {
        PaymentEventDetailDTO detail = new PaymentEventDetailDTO();
        detail.setEventNo("EVT-001");
        detail.setEventPayload("{\"customerName\":\"张女士\",\"phone\":\"13800138000\"}");
        when(paymentEventMapper.findDetail("EVT-001")).thenReturn(detail);

        PaymentEventDetailDTO result = new PaymentEventServiceImpl(paymentEventMapper).detail(" EVT-001 ");

        assertEquals("EVT-001", result.getEventNo());
        assertTrue(result.getEventPayload().contains("张*"));
        assertTrue(result.getEventPayload().contains("138****8000"));
        assertTrue(detail.getEventPayload().contains("13800138000"));
        verify(paymentEventMapper).findDetail("EVT-001");
    }

    @Test
    void shouldRejectInvalidEventDetailNoBeforeMapperAccess() {
        PaymentEventServiceImpl service = new PaymentEventServiceImpl(paymentEventMapper);

        assertThrows(IllegalArgumentException.class, () -> service.detail("  "));
        assertThrows(IllegalArgumentException.class, () -> service.detail("EVT-001\n"));
        assertThrows(IllegalArgumentException.class,
                () -> service.detail(String.join("", Collections.nCopies(65, "E"))));

        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldThrowNotFoundWhenPaymentEventDetailMissing() {
        when(paymentEventMapper.findDetail("EVT-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper).detail("EVT-MISSING"));

        assertEquals(ErrorCode.PAYMENT_EVENT_NOT_FOUND, exception.getCode());
        verify(paymentEventMapper).findDetail("EVT-MISSING");
    }

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
        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldRejectInvalidRepublishEventNoBeforeDispatch() {
        PaymentEventRepublishRequestDTO oversizedEventNo = new PaymentEventRepublishRequestDTO();
        oversizedEventNo.setEventNo(String.join("", Collections.nCopies(65, "E")));
        oversizedEventNo.setOperator("finance-ops");
        PaymentEventRepublishRequestDTO controlEventNo = new PaymentEventRepublishRequestDTO();
        controlEventNo.setEventNo("EVT001\n");
        controlEventNo.setOperator("finance-ops");
        PaymentEventServiceImpl service = new PaymentEventServiceImpl(
                paymentEventMapper, paymentEventDispatchService);

        assertThrows(IllegalArgumentException.class,
                () -> service.republish(oversizedEventNo, new PaymentEventQueryDTO()));
        assertThrows(IllegalArgumentException.class,
                () -> service.republish(controlEventNo, new PaymentEventQueryDTO()));
        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldRejectOversizedEventQueryFieldsBeforeMapperAccess() {
        PaymentEventQueryDTO paymentOrderQuery = new PaymentEventQueryDTO();
        paymentOrderQuery.setPaymentOrderId(String.join("", Collections.nCopies(65, "P")));
        PaymentEventQueryDTO eventTypeQuery = new PaymentEventQueryDTO();
        eventTypeQuery.setEventType(String.join("", Collections.nCopies(65, "E")));
        PaymentEventQueryDTO publishStatusQuery = new PaymentEventQueryDTO();
        publishStatusQuery.setPublishStatus(String.join("", Collections.nCopies(33, "S")));
        PaymentEventQueryDTO downstreamQuery = new PaymentEventQueryDTO();
        downstreamQuery.setDownstreamSystem(String.join("", Collections.nCopies(257, "D")));
        PaymentEventQueryDTO eventTopicQuery = new PaymentEventQueryDTO();
        eventTopicQuery.setEventTopic(String.join("", Collections.nCopies(129, "T")));
        PaymentEventServiceImpl service = new PaymentEventServiceImpl(paymentEventMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(eventTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(publishStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(downstreamQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(eventTopicQuery));
        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldRejectControlCharactersInEventQueryFieldsBeforeMapperAccess() {
        PaymentEventQueryDTO paymentOrderQuery = new PaymentEventQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        PaymentEventQueryDTO eventTypeQuery = new PaymentEventQueryDTO();
        eventTypeQuery.setEventType("PAYMENT_SUCCESS\n");
        PaymentEventQueryDTO publishStatusQuery = new PaymentEventQueryDTO();
        publishStatusQuery.setPublishStatus("FAILED\n");
        PaymentEventQueryDTO downstreamQuery = new PaymentEventQueryDTO();
        downstreamQuery.setDownstreamSystem("accounting-system\n");
        PaymentEventQueryDTO eventTopicQuery = new PaymentEventQueryDTO();
        eventTopicQuery.setEventTopic("payment.trade\n");
        PaymentEventServiceImpl service = new PaymentEventServiceImpl(paymentEventMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(eventTypeQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(publishStatusQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(downstreamQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(eventTopicQuery));
        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldRejectRepublishWithoutValidOperatorBeforeDispatch() {
        PaymentEventRepublishRequestDTO blankOperator = new PaymentEventRepublishRequestDTO();
        blankOperator.setEventNo("EVT001");
        blankOperator.setOperator("   ");
        PaymentEventRepublishRequestDTO oversizedOperator = new PaymentEventRepublishRequestDTO();
        oversizedOperator.setEventNo("EVT001");
        oversizedOperator.setOperator(String.join("", Collections.nCopies(65, "A")));
        PaymentEventRepublishRequestDTO controlOperator = new PaymentEventRepublishRequestDTO();
        controlOperator.setEventNo("EVT001");
        controlOperator.setOperator("finance-ops\u0085");

        PaymentEventServiceImpl service = new PaymentEventServiceImpl(
                paymentEventMapper, paymentEventDispatchService);

        assertThrows(IllegalArgumentException.class,
                () -> service.republish(blankOperator, new PaymentEventQueryDTO()));
        assertThrows(IllegalArgumentException.class,
                () -> service.republish(oversizedOperator, new PaymentEventQueryDTO()));
        assertThrows(IllegalArgumentException.class,
                () -> service.republish(controlOperator, new PaymentEventQueryDTO()));
        verifyNoInteractions(paymentEventMapper, paymentEventDispatchService);
    }

    @Test
    void shouldRepublishEventAndReloadList() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo("EVT001");
        request.setOperator(" finance-ops ");
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setEventTopic(" payment.trade ");
        query.setSortField(" nextRetryAt ");
        query.setSortOrder(" ASC ");
        when(paymentEventDispatchService.republish("EVT001", "finance-ops")).thenReturn(true);
        when(paymentEventMapper.updateLastRepublishAudit("EVT001", "finance-ops")).thenReturn(1);
        when(paymentEventMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentEventMapper.count(query)).thenReturn(0L);

        new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService).republish(request, query);

        verify(paymentEventDispatchService).republish("EVT001", "finance-ops");
        verify(paymentEventMapper).updateLastRepublishAudit("EVT001", "finance-ops");
        assertEquals("payment.trade", query.getEventTopic());
        assertEquals("nextRetryAt", query.getSortField());
        assertEquals("asc", query.getSortOrder());
    }

    @Test
    void shouldRejectRepublishWhenDispatchServiceReturnsFalse() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo(" EVT404 ");
        request.setOperator("finance-ops");

        when(paymentEventDispatchService.republish("EVT404", "finance-ops")).thenReturn(false);

        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService)
                        .republish(request, new PaymentEventQueryDTO())
        );

        assertEquals("支付事件不存在、不可重发或投递失败", exception.getMessage());
        verify(paymentEventDispatchService).republish("EVT404", "finance-ops");
    }

    @Test
    void shouldPropagateUnknownRefundSubmitReviewError() {
        PaymentEventRepublishRequestDTO request = new PaymentEventRepublishRequestDTO();
        request.setEventNo(" EVT-UNKNOWN ");
        request.setOperator("finance-ops");
        when(paymentEventDispatchService.republish("EVT-UNKNOWN", "finance-ops"))
                .thenThrow(new BusinessException(
                        ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED,
                        "review required"));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> new PaymentEventServiceImpl(paymentEventMapper, paymentEventDispatchService)
                        .republish(request, new PaymentEventQueryDTO()));

        assertEquals(ErrorCode.REFUND_CHANNEL_SUBMIT_REVIEW_REQUIRED, exception.getCode());
        verify(paymentEventDispatchService).republish("EVT-UNKNOWN", "finance-ops");
    }

    @Test
    void shouldExportCsvWithCombinedFailedAndDeadLetterFilter() {
        PaymentEventQueryDTO query = new PaymentEventQueryDTO();
        query.setPublishStatus(" FAILED_OR_DEAD_LETTER ");
        PaymentEventListItemDTO item = new PaymentEventListItemDTO();
        item.setEventNo("EVT-001");
        item.setPublishStatus("DEAD_LETTER");
        item.setPaymentSuccessHttpStage("CLEARING_ACCEPTED");
        item.setLastRepublishOperator("finance-ops");
        item.setLastRepublishedAt("2026-09-10 21:30:00");
        item.setEventPayload("{\"reason\":\"retry exhausted\",\"accessToken\":\"token-raw\"}");
        when(paymentEventMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentEventServiceImpl(paymentEventMapper).exportCsv(query);

        assertEquals("FAILED_OR_DEAD_LETTER", query.getPublishStatus());
        assertTrue(csv.contains("EVT-001"));
        assertTrue(csv.contains("DEAD_LETTER"));
        assertTrue(csv.contains("HTTP投递阶段"));
        assertTrue(csv.contains("CLEARING_ACCEPTED"));
        assertTrue(csv.contains("finance-ops"));
        assertTrue(csv.contains("2026-09-10 21:30:00"));
        assertTrue(csv.contains("retry exhausted"));
        assertTrue(csv.contains("[REDACTED]"));
        assertFalse(csv.contains("token-raw"));
        assertTrue(item.getEventPayload().contains("token-raw"));
        verify(paymentEventMapper).findAllForExport(query);
    }
}
