package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
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
        item.setRequestStatus("成功");
        item.setRequestPayload("{\"amount\":\"88\"}");
        item.setResponsePayload("{\"message\":\"ok\"}");
        when(paymentRequestMapper.findAllForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new PaymentRequestServiceImpl(paymentRequestMapper).exportCsv(query);

        assertEquals("成功", query.getRequestStatus());
        assertTrue(csv.contains("REQ-001"));
        assertTrue(csv.contains("\"{\"\"amount\"\":\"\"88\"\"}\""));
        assertTrue(csv.contains("\"{\"\"message\"\":\"\"ok\"\"}\""));
        verify(paymentRequestMapper).findAllForExport(query);
    }
}
