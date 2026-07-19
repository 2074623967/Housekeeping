package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.entity.OrderEntity;

import java.util.List;

public interface OrderMapper{

    List<OrderListItemDTO> findAll();
}