package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentLogDetailDTO;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogOverviewDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import java.util.Collections;
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
    void shouldNormalizeAndReturnDetail() {
        PaymentLogDetailDTO detail = new PaymentLogDetailDTO();
        detail.setLogNo("LOG-001");
        when(paymentLogMapper.findDetail("LOG-001")).thenReturn(detail);

        PaymentLogDetailDTO result = new PaymentLogServiceImpl(paymentLogMapper).detail(" LOG-001 ");

        assertEquals("LOG-001", result.getLogNo());
        verify(paymentLogMapper).findDetail("LOG-001");
    }

    @Test
    void shouldRejectInvalidLogNoBeforeMapperAccess() {
        PaymentLogServiceImpl service = new PaymentLogServiceImpl(paymentLogMapper);

        BusinessException blank = assertThrows(BusinessException.class, () -> service.detail("  "));
        BusinessException control = assertThrows(BusinessException.class, () -> service.detail("LOG-001\n"));
        BusinessException oversized = assertThrows(BusinessException.class, () -> service.detail(repeat("L", 65)));

        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, blank.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, control.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, oversized.getCode());
        verifyNoInteractions(paymentLogMapper);
    }

    @Test
    void shouldThrowNotFoundWhenPaymentLogDetailMissing() {
        when(paymentLogMapper.findDetail("LOG-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentLogServiceImpl(paymentLogMapper).detail("LOG-MISSING"));

        assertEquals(ErrorCode.PAYMENT_LOG_NOT_FOUND, exception.getCode());
        verify(paymentLogMapper).findDetail("LOG-MISSING");
    }

    @Test
    void shouldNormalizeAndForwardPagingQuery() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setProcessStage("支付提交");
        query.setLogLevel("INFO");
        query.setSource(" wx_h5 ");
        query.setKeyword(" 回调 ");
        query.setSortField(" logLevel ");
        query.setSortOrder(" ASC ");
        query.setPageNo(3);
        query.setPageSize(25);

        new PaymentLogServiceImpl(paymentLogMapper).list(query);

        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("wx_h5", query.getSource());
        assertEquals("回调", query.getKeyword());
        assertEquals("logLevel", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(paymentLogMapper).findAll(query);
        verify(paymentLogMapper).count(query);
    }

    @Test
    void shouldReturnEmptyPageWhenMapperReturnsNullList() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        when(paymentLogMapper.findAll(query)).thenReturn(null);
        when(paymentLogMapper.count(query)).thenReturn(0L);

        assertTrue(new PaymentLogServiceImpl(paymentLogMapper).list(query).getItems().isEmpty());
        verify(paymentLogMapper).findAll(query);
        verify(paymentLogMapper).count(query);
    }

    @Test
    void shouldIgnoreNullRowsWhenListingPaymentLogs() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        PaymentLogListItemDTO item = new PaymentLogListItemDTO();
        item.setLogNo("LOG-001");
        when(paymentLogMapper.findAll(query))
                .thenReturn(java.util.Arrays.asList((PaymentLogListItemDTO) null, item));
        when(paymentLogMapper.count(query)).thenReturn(1L);

        PageResultDTO<PaymentLogListItemDTO> result =
                new PaymentLogServiceImpl(paymentLogMapper).list(query);

        assertEquals(1, result.getItems().size());
        assertEquals("LOG-001", result.getItems().get(0).getLogNo());
    }

    @Test
    void shouldRejectOversizedPaymentLogQueryBeforeMapperAccess() {
        PaymentLogQueryDTO paymentOrderQuery = new PaymentLogQueryDTO();
        paymentOrderQuery.setPaymentOrderId(repeat("P", 65));
        PaymentLogQueryDTO orderQuery = new PaymentLogQueryDTO();
        orderQuery.setOrderNo(repeat("O", 65));
        PaymentLogQueryDTO processStageQuery = new PaymentLogQueryDTO();
        processStageQuery.setProcessStage(repeat("S", 33));
        PaymentLogQueryDTO logLevelQuery = new PaymentLogQueryDTO();
        logLevelQuery.setLogLevel(repeat("L", 17));
        PaymentLogQueryDTO sourceQuery = new PaymentLogQueryDTO();
        sourceQuery.setSource(repeat("C", 65));
        PaymentLogQueryDTO keywordQuery = new PaymentLogQueryDTO();
        keywordQuery.setKeyword(repeat("K", 16384));
        PaymentLogServiceImpl service = new PaymentLogServiceImpl(paymentLogMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(processStageQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(logLevelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(sourceQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(keywordQuery));
        verifyNoInteractions(paymentLogMapper);
    }

    @Test
    void shouldRejectControlCharactersInPaymentLogQueryBeforeMapperAccess() {
        PaymentLogQueryDTO paymentOrderQuery = new PaymentLogQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        PaymentLogQueryDTO orderQuery = new PaymentLogQueryDTO();
        orderQuery.setOrderNo("ORD-001\n");
        PaymentLogQueryDTO processStageQuery = new PaymentLogQueryDTO();
        processStageQuery.setProcessStage("渠道回调\n");
        PaymentLogQueryDTO logLevelQuery = new PaymentLogQueryDTO();
        logLevelQuery.setLogLevel("ERROR\n");
        PaymentLogQueryDTO sourceQuery = new PaymentLogQueryDTO();
        sourceQuery.setSource("wx_h5\n");
        PaymentLogQueryDTO keywordQuery = new PaymentLogQueryDTO();
        keywordQuery.setKeyword("回调\n");
        PaymentLogServiceImpl service = new PaymentLogServiceImpl(paymentLogMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(processStageQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(logLevelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(sourceQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(keywordQuery));
        verifyNoInteractions(paymentLogMapper);
    }

    @Test
    void shouldExportCsvWithNormalizedFieldsAndEscapedMessage() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setSource(" payment-core ");
        query.setKeyword(" 回调 ");
        PaymentLogListItemDTO item = new PaymentLogListItemDTO();
        item.setLogNo("LOG-001");
        item.setMessage("{\"trace\":\"ok\"}");
        when(paymentLogMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentLogServiceImpl(paymentLogMapper).exportCsv(query);

        assertEquals("payment-core", query.getSource());
        assertEquals("回调", query.getKeyword());
        assertTrue(csv.contains("LOG-001"));
        assertTrue(csv.contains("\"{\"\"trace\"\":\"\"ok\"\"}\""));
        verify(paymentLogMapper).findAllForExport(query);
    }

    @Test
    void shouldNormalizeAndReturnOverview() {
        PaymentLogQueryDTO query = new PaymentLogQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setSource(" wx_h5 ");
        query.setKeyword(" 回调 ");
        PaymentLogOverviewDTO overview = new PaymentLogOverviewDTO();
        overview.setTotalLogCount(20L);
        overview.setCallbackErrorCount(3);
        when(paymentLogMapper.findOverviewSummary(query)).thenReturn(overview);

        PaymentLogOverviewDTO result = new PaymentLogServiceImpl(paymentLogMapper).overview(query);

        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("wx_h5", query.getSource());
        assertEquals("回调", query.getKeyword());
        assertEquals(20L, result.getTotalLogCount());
        assertEquals(3, result.getCallbackErrorCount());
        assertEquals(0, result.getEventWarnCount());
        verify(paymentLogMapper).findOverviewSummary(query);
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
