package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.mapper.OrderMapper;
import com.abc123.hsp.service.OrderService;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public List<OrderListItemDTO> list() {
        return orderMapper.findAll();
    }
}
