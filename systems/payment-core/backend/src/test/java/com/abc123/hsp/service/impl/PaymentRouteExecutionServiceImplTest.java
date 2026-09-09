package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.mapper.PaymentRouteExecutionMapper;
import java.util.Collections;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 支付路由执行结果查询测试。
 */
@ExtendWith(MockitoExtension.class)
class PaymentRouteExecutionServiceImplTest {

    @Mock
    private PaymentRouteExecutionMapper paymentRouteExecutionMapper;

    @Test
    void shouldNormalizeAndForwardRouteExecutionPagingQuery() {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setRouteRule(" RULE_HOME_WX ");
        query.setChannelCode(" wx_h5 ");
        query.setPaymentMethod(" 微信 ");
        query.setTerminal(" H5 ");
        query.setRouteResult(" 命中规则路由 ");
        query.setSortField(" channelCode ");
        query.setSortOrder(" ASC ");
        query.setPageNo(-2);
        query.setPageSize(0);

        new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper).list(query);

        Assertions.assertEquals("PAY-001", query.getPaymentOrderId());
        Assertions.assertEquals("ORD-001", query.getOrderNo());
        Assertions.assertEquals("RULE_HOME_WX", query.getRouteRule());
        Assertions.assertEquals("wx_h5", query.getChannelCode());
        Assertions.assertEquals("微信", query.getPaymentMethod());
        Assertions.assertEquals("H5", query.getTerminal());
        Assertions.assertEquals("命中规则路由", query.getRouteResult());
        Assertions.assertEquals("channelCode", query.getSortField());
        Assertions.assertEquals("asc", query.getSortOrder());
        Assertions.assertEquals(1, query.getPageNo());
        Assertions.assertEquals(1, query.getPageSize());
        verify(paymentRouteExecutionMapper).findAll(query);
        verify(paymentRouteExecutionMapper).count(query);
    }

    @Test
    void shouldNormalizeAndReturnRouteExecutionOverview() {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setOrderNo(" ORD-001 ");
        query.setRouteRule(" RULE_HOME_WX ");
        query.setChannelCode(" wx_h5 ");
        query.setPaymentMethod(" 微信 ");
        query.setTerminal(" H5 ");
        query.setRouteResult(" 命中规则路由 ");
        PaymentRouteExecutionOverviewDTO overview = new PaymentRouteExecutionOverviewDTO();
        overview.setTotalRouteCount(8L);
        overview.setWarnRouteCount(2L);
        org.mockito.Mockito.when(paymentRouteExecutionMapper.findOverview(query)).thenReturn(overview);

        PaymentRouteExecutionOverviewDTO result = new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper).overview(query);

        Assertions.assertEquals("PAY-001", query.getPaymentOrderId());
        Assertions.assertEquals("ORD-001", query.getOrderNo());
        Assertions.assertEquals("RULE_HOME_WX", query.getRouteRule());
        Assertions.assertEquals("wx_h5", query.getChannelCode());
        Assertions.assertEquals("微信", query.getPaymentMethod());
        Assertions.assertEquals("H5", query.getTerminal());
        Assertions.assertEquals("命中规则路由", query.getRouteResult());
        Assertions.assertEquals(8L, result.getTotalRouteCount());
        Assertions.assertEquals(2L, result.getWarnRouteCount());
        Assertions.assertEquals(0, result.getOfflineRouteCount());
        Assertions.assertEquals(Boolean.FALSE, result.getRouteHealthEnabled());
        Assertions.assertEquals(24, result.getRouteHealthLookbackHours());
        Assertions.assertEquals(5, result.getRouteHealthFailureCountThreshold());
        Assertions.assertEquals(30, result.getRouteHealthFailureRateThresholdPercent());
        Assertions.assertEquals(10, result.getRouteHealthMinimumSampleCount());
        verify(paymentRouteExecutionMapper).findOverview(query);
    }

    @Test
    void shouldMaskRouteContextWithoutMutatingMapperFacts() {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        PaymentRouteExecutionListItemDTO item = new PaymentRouteExecutionListItemDTO();
        item.setCustomerName("张三");
        item.setClientIp("10.20.30.40");
        item.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        item.setRequestPayload("{\"payerPhone\":\"13800138000\"}");
        item.setResponsePayload("{\"cardNo\":\"6222021234567890123\"}");
        org.mockito.Mockito.when(paymentRouteExecutionMapper.findAll(query))
                .thenReturn(Collections.singletonList(item));
        org.mockito.Mockito.when(paymentRouteExecutionMapper.count(query)).thenReturn(1L);

        PaymentRouteExecutionListItemDTO result = new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper)
                .list(query)
                .getItems()
                .get(0);

        assertEquals("张*", result.getCustomerName());
        assertEquals("10.20.*.*", result.getClientIp());
        assertEquals("ORDER-****DEFG", result.getIdempotencyKey());
        assertTrue(result.getRequestPayload().contains("138****8000"));
        assertTrue(result.getResponsePayload().contains("622202******0123"));
        assertEquals("张三", item.getCustomerName());
        assertEquals("10.20.30.40", item.getClientIp());
    }
}
