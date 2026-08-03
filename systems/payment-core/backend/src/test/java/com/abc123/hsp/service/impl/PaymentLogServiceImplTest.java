package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
}
