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
import com.abc123.hsp.dto.PaymentRequestDetailDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import java.util.Arrays;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付请求分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRequestServiceImplTest {

    @Mock
    private PaymentRequestMapper paymentRequestMapper;

    @Test
    void shouldNormalizeAndMaskPaymentRequestDetailWithoutChangingMapperDto() {
        PaymentRequestDetailDTO mapperDetail = new PaymentRequestDetailDTO();
        mapperDetail.setRequestNo("REQ-001");
        mapperDetail.setClientIp("10.20.30.40");
        mapperDetail.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        mapperDetail.setRequestPayload("{\"clientIp\":\"10.20.30.40\",\"customerName\":\"张三\"}");
        mapperDetail.setResponsePayload("{\"mobile\":\"13800138000\"}");
        when(paymentRequestMapper.findDetail("REQ-001")).thenReturn(mapperDetail);

        PaymentRequestDetailDTO result =
                new PaymentRequestServiceImpl(paymentRequestMapper).detail(" REQ-001 ");

        assertEquals("REQ-001", result.getRequestNo());
        assertEquals("10.20.*.*", result.getClientIp());
        assertEquals("ORDER-****DEFG", result.getIdempotencyKey());
        assertTrue(result.getRequestPayload().contains("张*"));
        assertTrue(result.getResponsePayload().contains("138****8000"));
        assertEquals("10.20.30.40", mapperDetail.getClientIp());
        assertTrue(mapperDetail.getRequestPayload().contains("张三"));
        verify(paymentRequestMapper).findDetail("REQ-001");
    }

    @Test
    void shouldRejectInvalidPaymentRequestDetailIdentifierBeforeMapperAccess() {
        PaymentRequestServiceImpl service = new PaymentRequestServiceImpl(paymentRequestMapper);

        BusinessException blank = assertThrows(BusinessException.class, () -> service.detail("  "));
        BusinessException control = assertThrows(
                BusinessException.class, () -> service.detail("REQ-001\n"));
        BusinessException oversized = assertThrows(
                BusinessException.class, () -> service.detail(repeat("R", 64) + " "));

        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, blank.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, control.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, oversized.getCode());
        verifyNoInteractions(paymentRequestMapper);
    }

    @Test
    void shouldReportMissingPaymentRequestDetail() {
        when(paymentRequestMapper.findDetail("REQ-MISSING")).thenReturn(null);

        BusinessException missing = assertThrows(BusinessException.class,
                () -> new PaymentRequestServiceImpl(paymentRequestMapper).detail("REQ-MISSING"));

        assertEquals(ErrorCode.PAYMENT_REQUEST_NOT_FOUND, missing.getCode());
        assertEquals("支付请求不存在", missing.getMessage());
    }

    @Test
    void shouldNormalizeAndForwardPagingQuery() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestNo(" PR-001 ");
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setClientIp(" 127.0.0.1 ");
        query.setRequestStatus(" 请求已发起 ");
        query.setSortField(" channelCode ");
        query.setSortOrder(" ASC ");
        query.setPageNo(2);
        query.setPageSize(50);

        new PaymentRequestServiceImpl(paymentRequestMapper).list(query);

        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("127.0.0.1", query.getClientIp());
        assertEquals("处理中", query.getRequestStatus());
        assertEquals("channelCode", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(paymentRequestMapper).findAll(query);
        verify(paymentRequestMapper).count(query);
    }

    @Test
    void shouldRejectOversizedPaymentRequestQueryBeforeMapperAccess() {
        PaymentRequestQueryDTO requestNoQuery = new PaymentRequestQueryDTO();
        requestNoQuery.setRequestNo(repeat("R", 65));
        PaymentRequestQueryDTO paymentOrderQuery = new PaymentRequestQueryDTO();
        paymentOrderQuery.setPaymentOrderId(repeat("P", 65));
        PaymentRequestQueryDTO orderQuery = new PaymentRequestQueryDTO();
        orderQuery.setOrderNo(repeat("O", 65));
        PaymentRequestQueryDTO channelQuery = new PaymentRequestQueryDTO();
        channelQuery.setChannelCode(repeat("C", 65));
        PaymentRequestQueryDTO terminalQuery = new PaymentRequestQueryDTO();
        terminalQuery.setTerminal(repeat("T", 33));
        PaymentRequestQueryDTO clientIpQuery = new PaymentRequestQueryDTO();
        clientIpQuery.setClientIp(repeat("I", 65));
        PaymentRequestQueryDTO statusQuery = new PaymentRequestQueryDTO();
        statusQuery.setRequestStatus(repeat("S", 33));
        PaymentRequestServiceImpl service = new PaymentRequestServiceImpl(paymentRequestMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(requestNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(clientIpQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(statusQuery));
        verifyNoInteractions(paymentRequestMapper);
    }

    @Test
    void shouldRejectControlCharactersInPaymentRequestQueryBeforeMapperAccess() {
        PaymentRequestQueryDTO requestNoQuery = new PaymentRequestQueryDTO();
        requestNoQuery.setRequestNo("REQ-001\n");
        PaymentRequestQueryDTO paymentOrderQuery = new PaymentRequestQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        PaymentRequestQueryDTO orderQuery = new PaymentRequestQueryDTO();
        orderQuery.setOrderNo("ORD-001\n");
        PaymentRequestQueryDTO channelQuery = new PaymentRequestQueryDTO();
        channelQuery.setChannelCode("wx_h5\n");
        PaymentRequestQueryDTO terminalQuery = new PaymentRequestQueryDTO();
        terminalQuery.setTerminal("H5\n");
        PaymentRequestQueryDTO clientIpQuery = new PaymentRequestQueryDTO();
        clientIpQuery.setClientIp("127.0.0.1\n");
        PaymentRequestQueryDTO statusQuery = new PaymentRequestQueryDTO();
        statusQuery.setRequestStatus("请求成功\n");
        PaymentRequestServiceImpl service = new PaymentRequestServiceImpl(paymentRequestMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(requestNoQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(clientIpQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(statusQuery));
        verifyNoInteractions(paymentRequestMapper);
    }

    @Test
    void shouldExportCsvWithNormalizedStatusAndEscapedPayload() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestStatus("请求成功");
        PaymentRequestListItemDTO item = new PaymentRequestListItemDTO();
        item.setRequestNo("REQ-001");
        item.setPaymentOrderId("PAY-001");
        item.setPayScene("HOME_CLEAN");
        item.setClientIp("127.0.0.1");
        item.setIdempotencyKey("IDEMPOTENCY-1234567890");
        item.setRequestStatus("成功");
        item.setRequestStatusType("success");
        item.setRequestPayload("{\"amount\":\"88\",\"clientIp\":\"127.0.0.1\",\"idempotencyKey\":\"IDEMPOTENCY-1234567890\",\"mobile\":\"13800138000\"}");
        item.setResponsePayload("{\"message\":\"ok\",\"cardNo\":\"6222021234567890123\"}");
        when(paymentRequestMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentRequestServiceImpl(paymentRequestMapper).exportCsv(query);

        assertEquals("成功", query.getRequestStatus());
        assertTrue(csv.contains("支付单号,业务链路,预付单号"));
        assertTrue(csv.contains("预付单号,支付场景,订单号"));
        assertTrue(csv.contains("HOME_CLEAN"));
        assertTrue(csv.contains("\"CHAIN-PAY-001\""));
        assertTrue(csv.contains("请求状态,状态样式,创建时间"));
        assertTrue(csv.contains("\"成功\",\"success\""));
        assertTrue(csv.contains("REQ-001"));
        assertTrue(csv.contains("127.0.*.*"));
        assertTrue(csv.contains("IDEMPO****7890"));
        assertTrue(csv.contains("\"{\"\"amount\"\":\"\"88\"\",\"\"clientIp\"\":\"\"127.0.*.*\"\",\"\"idempotencyKey\"\":\"\"IDEMPO****7890\"\",\"\"mobile\"\":\"\"138****8000\"\"}\""));
        assertTrue(csv.contains("\"{\"\"message\"\":\"\"ok\"\",\"\"cardNo\"\":\"\"622202******0123\"\"}\""));
        verify(paymentRequestMapper).findAllForExport(query);
    }

    @Test
    void shouldMaskSensitiveFieldsWhenListing() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        PaymentRequestListItemDTO item = new PaymentRequestListItemDTO();
        item.setClientIp("10.20.30.40");
        item.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        item.setRequestPayload("{\"clientIp\":\"10.20.30.40\",\"customerName\":\"张三\"}");
        item.setResponsePayload("{\"mobile\":\"13800138000\"}");
        when(paymentRequestMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        when(paymentRequestMapper.count(query)).thenReturn(1L);

        PaymentRequestListItemDTO resultItem = new PaymentRequestServiceImpl(paymentRequestMapper)
                .list(query)
                .getItems()
                .get(0);

        assertEquals("10.20.*.*", resultItem.getClientIp());
        assertEquals("ORDER-****DEFG", resultItem.getIdempotencyKey());
        assertTrue(resultItem.getRequestPayload().contains("张*"));
        assertTrue(resultItem.getResponsePayload().contains("138****8000"));
        assertEquals("10.20.30.40", item.getClientIp());
        assertTrue(item.getRequestPayload().contains("张三"));
    }

    @Test
    void shouldIgnoreNullRowsWhenListingPaymentRequests() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        PaymentRequestListItemDTO item = new PaymentRequestListItemDTO();
        item.setRequestNo("REQ-001");
        when(paymentRequestMapper.findAll(query)).thenReturn(Arrays.asList(null, item));
        when(paymentRequestMapper.count(query)).thenReturn(1L);

        PageResultDTO<PaymentRequestListItemDTO> result =
                new PaymentRequestServiceImpl(paymentRequestMapper).list(query);

        assertEquals(1, result.getItems().size());
        assertEquals("REQ-001", result.getItems().get(0).getRequestNo());
    }

    @Test
    void shouldNormalizeAndReturnOverview() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestStatus(" 请求失败 ");
        query.setChannelCode(" wx_h5 ");
        PaymentRequestOverviewDTO overview = new PaymentRequestOverviewDTO();
        overview.setTotalRequestCount(12L);
        overview.setRepeatedPaymentOrderCount(2);
        when(paymentRequestMapper.findOverviewSummary(query)).thenReturn(overview);

        PaymentRequestOverviewDTO result = new PaymentRequestServiceImpl(paymentRequestMapper).overview(query);

        assertEquals("失败", query.getRequestStatus());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals(12L, result.getTotalRequestCount());
        assertEquals(2, result.getRepeatedPaymentOrderCount());
        assertEquals(0, result.getMissingResponseCount());
        verify(paymentRequestMapper).findOverviewSummary(query);
    }

    @Test
    void shouldUseDefaultQueryWhenListingPaymentRequestsWithNullQuery() {
        when(paymentRequestMapper.findAll(org.mockito.ArgumentMatchers.any(PaymentRequestQueryDTO.class)))
                .thenReturn(Collections.emptyList());
        when(paymentRequestMapper.count(org.mockito.ArgumentMatchers.any(PaymentRequestQueryDTO.class))).thenReturn(0L);

        PageResultDTO<PaymentRequestListItemDTO> result =
                new PaymentRequestServiceImpl(paymentRequestMapper).list(null);

        assertEquals(0, result.getTotal());
        org.mockito.ArgumentCaptor<PaymentRequestQueryDTO> captor =
                org.mockito.ArgumentCaptor.forClass(PaymentRequestQueryDTO.class);
        verify(paymentRequestMapper).findAll(captor.capture());
        assertEquals(1, captor.getValue().getPageNo());
        assertEquals(20, captor.getValue().getPageSize());
    }

    private String repeat(String value, int count) {
        return String.join("", Collections.nCopies(count, value));
    }
}
