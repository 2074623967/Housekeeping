package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付流水分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentFlowServiceImplTest {

    @Mock
    private PaymentFlowMapper paymentFlowMapper;

    @Test
    void shouldNormalizeAndForwardPaymentFlowPagingQuery() {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setFlowType("支付尝试");
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setBusinessStatus(" WAIT_CALLBACK ");
        query.setKeyword(" 回调 ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(-3);
        query.setPageSize(0);

        when(paymentFlowMapper.findAll(query)).thenReturn(Collections.emptyList());
        when(paymentFlowMapper.count(query)).thenReturn(0L);
        new PaymentFlowServiceImpl(paymentFlowMapper).list(query);

        assertEquals(1, query.getPageNo());
        assertEquals(1, query.getPageSize());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        assertEquals("回调", query.getKeyword());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        verify(paymentFlowMapper).findAll(query);
        verify(paymentFlowMapper).count(query);
    }

    @Test
    void shouldExportPaymentFlowsInBatchesWithNormalizedFilters() {
        PaymentFlowQueryDTO query = new PaymentFlowQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setFlowType("支付尝试");
        query.setChannelCode(" wx_h5 ");
        query.setTerminal(" H5 ");
        query.setBusinessStatus(" WAIT_CALLBACK ");
        query.setKeyword(" 原始报文 ");
        query.setSortField(" retryCount ");
        query.setSortOrder(" ASC ");
        query.setPageNo(3);
        query.setPageSize(500);

        when(paymentFlowMapper.findAllForExport(query)).thenReturn(Collections.singletonList(buildItem("FLOW-001", "首笔\"摘要", 1)));

        String csv = new PaymentFlowServiceImpl(paymentFlowMapper).exportCsv(query);

        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("ORD-001", query.getOrderNo());
        assertEquals("wx_h5", query.getChannelCode());
        assertEquals("H5", query.getTerminal());
        assertEquals("WAIT_CALLBACK", query.getBusinessStatus());
        assertEquals("原始报文", query.getKeyword());
        assertEquals("retryCount", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals(3, query.getPageNo());
        assertEquals(100, query.getPageSize());
        assertTrue(csv.startsWith("\uFEFF流水编号,支付单号,订单号,预付单号,流水类型,类型标签"));
        assertTrue(csv.contains("\"FLOW-001\""));
        assertTrue(csv.contains("\"首笔\"\"摘要\""));
        assertTrue(csv.contains("\"info\""));
        assertTrue(csv.contains("\"WAIT_CALLBACK\""));
        verify(paymentFlowMapper).findAllForExport(query);
    }

    private PaymentFlowListItemDTO buildItem(String flowNo, String summary, Integer retryCount) {
        PaymentFlowListItemDTO item = new PaymentFlowListItemDTO();
        item.setFlowNo(flowNo);
        item.setPaymentOrderId("PAY-001");
        item.setOrderNo("ORD-001");
        item.setPrepayOrderNo("PRE-001");
        item.setFlowType("支付尝试");
        item.setFlowTypeTag("info");
        item.setChannelCode("wx_h5");
        item.setTerminal("H5");
        item.setClientIp("127.0.0.1");
        item.setIdempotencyKey("IDEMP-001");
        item.setBusinessStatus("WAIT_CALLBACK");
        item.setBusinessStatusType("warning");
        item.setNotifyType("支付回调");
        item.setRouteRule("默认优先");
        item.setDownstreamSystem("trade-center");
        item.setEventTopic("payment.events");
        item.setPublishStatus("SUCCESS");
        item.setRetryCount(retryCount);
        item.setRequestPayload("{\"amount\":6800}");
        item.setResponsePayload("{\"code\":\"SUCCESS\"}");
        item.setSummary(summary);
        item.setCreatedAt("2026-07-31 15:12:00");
        return item;
    }
}
