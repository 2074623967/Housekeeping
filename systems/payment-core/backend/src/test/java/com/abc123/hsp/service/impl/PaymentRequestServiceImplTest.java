package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.mapper.PaymentRequestMapper;
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
    void shouldExportCsvWithNormalizedStatusAndEscapedPayload() {
        PaymentRequestQueryDTO query = new PaymentRequestQueryDTO();
        query.setRequestStatus("请求成功");
        PaymentRequestListItemDTO item = new PaymentRequestListItemDTO();
        item.setRequestNo("REQ-001");
        item.setPaymentOrderId("PAY-001");
        item.setClientIp("127.0.0.1");
        item.setIdempotencyKey("IDEMPOTENCY-1234567890");
        item.setRequestStatus("成功");
        item.setRequestPayload("{\"amount\":\"88\",\"clientIp\":\"127.0.0.1\",\"idempotencyKey\":\"IDEMPOTENCY-1234567890\",\"mobile\":\"13800138000\"}");
        item.setResponsePayload("{\"message\":\"ok\",\"cardNo\":\"6222021234567890123\"}");
        when(paymentRequestMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentRequestServiceImpl(paymentRequestMapper).exportCsv(query);

        assertEquals("成功", query.getRequestStatus());
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
}
