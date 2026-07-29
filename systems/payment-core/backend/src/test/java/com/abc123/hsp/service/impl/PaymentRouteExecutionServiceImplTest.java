package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.mapper.PaymentRouteExecutionMapper;
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
}
