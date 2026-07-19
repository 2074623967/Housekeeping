package com.abc123.hsp.service;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.repository.OrderRepository;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private final OrderRepository orderRepository;

    public OrderService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    public List<OrderListItemDTO> list() {
        return orderRepository.findAll();
    }
}
