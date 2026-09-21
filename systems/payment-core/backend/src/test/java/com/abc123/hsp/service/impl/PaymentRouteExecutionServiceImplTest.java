package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PaymentRouteExecutionDetailDTO;
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
    void shouldNormalizeAndReturnMaskedRouteExecutionDetail() {
        PaymentRouteExecutionDetailDTO detail = new PaymentRouteExecutionDetailDTO();
        detail.setRouteNo("ROUTE-001");
        detail.setCustomerName("张三");
        detail.setClientIp("10.20.30.40");
        detail.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        detail.setRequestPayload("{\"payerPhone\":\"13800138000\"}");
        detail.setResponsePayload("{\"cardNo\":\"6222021234567890123\"}");
        when(paymentRouteExecutionMapper.findDetail("ROUTE-001")).thenReturn(detail);

        PaymentRouteExecutionDetailDTO result = new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper)
                .detail(" ROUTE-001 ");

        assertEquals("ROUTE-001", result.getRouteNo());
        assertEquals("张*", result.getCustomerName());
        assertEquals("10.20.*.*", result.getClientIp());
        assertEquals("ORDER-****DEFG", result.getIdempotencyKey());
        assertTrue(result.getRequestPayload().contains("138****8000"));
        assertTrue(result.getResponsePayload().contains("622202******0123"));
        assertEquals("张三", detail.getCustomerName());
        verify(paymentRouteExecutionMapper).findDetail("ROUTE-001");
    }

    @Test
    void shouldRejectInvalidRouteNoBeforeMapperAccess() {
        PaymentRouteExecutionServiceImpl service =
                new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper);

        BusinessException blank = assertThrows(BusinessException.class, () -> service.detail("  "));
        BusinessException control = assertThrows(BusinessException.class, () -> service.detail("ROUTE-001\n"));
        BusinessException oversized = assertThrows(BusinessException.class,
                () -> service.detail(repeat("R", 65)));

        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, blank.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, control.getCode());
        assertEquals(ErrorCode.PAYMENT_REQUEST_INVALID, oversized.getCode());
        verifyNoInteractions(paymentRouteExecutionMapper);
    }

    @Test
    void shouldThrowNotFoundWhenRouteDetailMissing() {
        when(paymentRouteExecutionMapper.findDetail("ROUTE-MISSING")).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper)
                        .detail("ROUTE-MISSING"));

        assertEquals(ErrorCode.PAYMENT_ROUTE_NOT_FOUND, exception.getCode());
        verify(paymentRouteExecutionMapper).findDetail("ROUTE-MISSING");
    }

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
    void shouldRejectOversizedRouteExecutionQueryBeforeMapperAccess() {
        PaymentRouteExecutionQueryDTO paymentOrderQuery = new PaymentRouteExecutionQueryDTO();
        paymentOrderQuery.setPaymentOrderId(repeat("P", 65));
        PaymentRouteExecutionQueryDTO orderQuery = new PaymentRouteExecutionQueryDTO();
        orderQuery.setOrderNo(repeat("O", 65));
        PaymentRouteExecutionQueryDTO routeRuleQuery = new PaymentRouteExecutionQueryDTO();
        routeRuleQuery.setRouteRule(repeat("R", 129));
        PaymentRouteExecutionQueryDTO channelQuery = new PaymentRouteExecutionQueryDTO();
        channelQuery.setChannelCode(repeat("C", 65));
        PaymentRouteExecutionQueryDTO paymentMethodQuery = new PaymentRouteExecutionQueryDTO();
        paymentMethodQuery.setPaymentMethod(repeat("M", 33));
        PaymentRouteExecutionQueryDTO terminalQuery = new PaymentRouteExecutionQueryDTO();
        terminalQuery.setTerminal(repeat("T", 33));
        PaymentRouteExecutionQueryDTO routeResultQuery = new PaymentRouteExecutionQueryDTO();
        routeResultQuery.setRouteResult(repeat("S", 129));
        PaymentRouteExecutionServiceImpl service =
                new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(routeRuleQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentMethodQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(routeResultQuery));
        verifyNoInteractions(paymentRouteExecutionMapper);
    }

    @Test
    void shouldRejectControlCharactersInRouteExecutionQueryBeforeMapperAccess() {
        PaymentRouteExecutionQueryDTO paymentOrderQuery = new PaymentRouteExecutionQueryDTO();
        paymentOrderQuery.setPaymentOrderId("PAY-001\n");
        PaymentRouteExecutionQueryDTO orderQuery = new PaymentRouteExecutionQueryDTO();
        orderQuery.setOrderNo("ORD-001\n");
        PaymentRouteExecutionQueryDTO routeRuleQuery = new PaymentRouteExecutionQueryDTO();
        routeRuleQuery.setRouteRule("RULE_HOME_WX\n");
        PaymentRouteExecutionQueryDTO channelQuery = new PaymentRouteExecutionQueryDTO();
        channelQuery.setChannelCode("wx_h5\n");
        PaymentRouteExecutionQueryDTO paymentMethodQuery = new PaymentRouteExecutionQueryDTO();
        paymentMethodQuery.setPaymentMethod("微信\n");
        PaymentRouteExecutionQueryDTO terminalQuery = new PaymentRouteExecutionQueryDTO();
        terminalQuery.setTerminal("H5\n");
        PaymentRouteExecutionQueryDTO routeResultQuery = new PaymentRouteExecutionQueryDTO();
        routeResultQuery.setRouteResult("命中规则路由\n");
        PaymentRouteExecutionServiceImpl service =
                new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper);

        assertThrows(IllegalArgumentException.class, () -> service.list(paymentOrderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(orderQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(routeRuleQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(channelQuery));
        assertThrows(IllegalArgumentException.class, () -> service.overview(paymentMethodQuery));
        assertThrows(IllegalArgumentException.class, () -> service.exportCsv(terminalQuery));
        assertThrows(IllegalArgumentException.class, () -> service.list(routeResultQuery));
        verifyNoInteractions(paymentRouteExecutionMapper);
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

    @Test
    void shouldExportAllMatchingRoutesWithMaskedContext() {
        PaymentRouteExecutionQueryDTO query = new PaymentRouteExecutionQueryDTO();
        query.setPaymentOrderId(" PAY-001 ");
        query.setRouteRule(" RULE_HOME_WX ");
        query.setPaymentMethod(" 微信 ");
        query.setSortField(" channelCode ");
        query.setSortOrder(" ASC ");
        query.setPageNo(9);
        query.setPageSize(500);
        PaymentRouteExecutionListItemDTO item = new PaymentRouteExecutionListItemDTO();
        item.setRouteNo("ROUTE-001");
        item.setPaymentOrderId("PAY-001");
        item.setCustomerName("张三");
        item.setRouteRule("amount>1000 => \"wx_h5\"");
        item.setClientIp("10.20.30.40");
        item.setIdempotencyKey("ORDER-REQUEST-20260803-ABCDEFG");
        item.setRequestPayload("{\"payerPhone\":\"13800138000\"}");
        item.setResponsePayload("{\"code\":\"SUCCESS\"}");
        item.setOpsConfigSnapshotVersion(12L);
        when(paymentRouteExecutionMapper.findAllForExport(query))
                .thenReturn(Collections.singletonList(item));

        String csv = new PaymentRouteExecutionServiceImpl(paymentRouteExecutionMapper).exportCsv(query);

        assertEquals("PAY-001", query.getPaymentOrderId());
        assertEquals("RULE_HOME_WX", query.getRouteRule());
        assertEquals("微信", query.getPaymentMethod());
        assertEquals("channelCode", query.getSortField());
        assertEquals("asc", query.getSortOrder());
        assertEquals(9, query.getPageNo());
        assertEquals(100, query.getPageSize());
        assertTrue(csv.startsWith("\uFEFF路由记录号,支付单号,订单号,预付单号"));
        assertTrue(csv.contains("\"张*\""));
        assertTrue(csv.contains("\"10.20.*.*\""));
        assertTrue(csv.contains("\"ORDER-****DEFG\""));
        assertTrue(csv.contains("138****8000"));
        assertTrue(csv.contains("\"amount>1000 => \"\"wx_h5\"\"\""));
        assertTrue(csv.contains("\"12\""));
        assertEquals("张三", item.getCustomerName());
        verify(paymentRouteExecutionMapper).findAllForExport(query);
    }

    private String repeat(String value, int times) {
        return String.join("", Collections.nCopies(times, value));
    }
}
