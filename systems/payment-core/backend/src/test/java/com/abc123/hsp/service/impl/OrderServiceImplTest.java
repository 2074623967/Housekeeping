package com.abc123.hsp.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.OrderMapper;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 订单中心分页查询测试。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderMapper orderMapper;

    @Test
    void shouldNormalizeAndForwardOrderPagingQuery() {
        OrderQueryDTO query = new OrderQueryDTO();
        query.setOrderNo(" ORD-001 ");
        query.setServiceType("深度保洁");
        query.setOrderStatus("待支付");
        query.setPageNo(0);
        query.setPageSize(500);

        org.mockito.Mockito.when(orderMapper.findAll(query)).thenReturn(Collections.emptyList());
        org.mockito.Mockito.when(orderMapper.count(query)).thenReturn(0L);

        new OrderServiceImpl(orderMapper).list(query);

        org.junit.jupiter.api.Assertions.assertEquals("ORD-001", query.getOrderNo());
        org.junit.jupiter.api.Assertions.assertEquals(1, query.getPageNo());
        org.junit.jupiter.api.Assertions.assertEquals(100, query.getPageSize());
        verify(orderMapper).findAll(query);
        verify(orderMapper).count(query);
    }

    @Test
    void shouldMaskOrderNamesWithoutChangingMapperDto() {
        OrderQueryDTO query = new OrderQueryDTO();
        OrderListItemDTO item = new OrderListItemDTO();
        item.setCustomerName("王阿姨");
        item.setWorkerName("李师傅");
        org.mockito.Mockito.when(orderMapper.findAll(query)).thenReturn(Collections.singletonList(item));
        org.mockito.Mockito.when(orderMapper.count(query)).thenReturn(1L);

        PageResultDTO<OrderListItemDTO> result = new OrderServiceImpl(orderMapper).list(query);

        assertEquals("王*", result.getItems().get(0).getCustomerName());
        assertEquals("李*", result.getItems().get(0).getWorkerName());
        assertEquals("王阿姨", item.getCustomerName());
        assertEquals("李师傅", item.getWorkerName());
    }

    @Test
    void shouldExportOrderCsv() {
        OrderQueryDTO query = new OrderQueryDTO();
        query.setOrderNo("ORD-001");
        query.setServiceType("深度保洁");
        query.setOrderStatus("待支付");

        OrderListItemDTO item = new OrderListItemDTO();
        item.setOrderNo("ORD-001");
        item.setCustomerName("王阿姨");
        item.setServiceType("=HYPERLINK(\"https://example.invalid\",\"深度保洁\")");
        item.setWorkerName("李师傅");
        item.setOrderAmount("¥188.00");
        item.setPaidAmount("¥0.00");
        item.setOrderStatus("待支付");
        item.setFulfillmentStatus("待履约");
        item.setLatestPaymentStatus("未发起");
        item.setLatestCashierStatus("未生成");
        item.setCreatedAt("2026-08-07 10:00:00");

        org.mockito.Mockito.when(orderMapper.findForExport(query)).thenReturn(Collections.singletonList(item));

        String csv = new OrderServiceImpl(orderMapper).exportCsv(query);

        assertTrue(csv.contains("订单号,账单号,客户名称"));
        assertTrue(csv.contains("ORD-001"));
        assertTrue(csv.contains("'=HYPERLINK(\"\"https://example.invalid\"\",\"\"深度保洁\"\")"));
        assertFalse(csv.contains("\"=HYPERLINK"));
        assertTrue(csv.contains("王*"));
        assertTrue(csv.contains("李*"));
        assertFalse(csv.contains("王阿姨"));
        assertFalse(csv.contains("李师傅"));
        assertEquals("王阿姨", item.getCustomerName());
        assertEquals("李师傅", item.getWorkerName());
    }
}
