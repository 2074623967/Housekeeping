package com.abc123.hsp.service;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;

public interface OrderService {

    /**
     * 分页查询订单中心列表。
     */
    PageResultDTO<OrderListItemDTO> list(OrderQueryDTO query);
}
