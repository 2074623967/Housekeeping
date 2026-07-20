package com.abc123.hsp.service.impl;

import static org.mockito.Mockito.verify;

import com.abc123.hsp.dto.OrderQueryDTO;
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
}
