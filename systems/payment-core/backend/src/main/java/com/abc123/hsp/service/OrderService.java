package com.abc123.hsp.service;

import com.abc123.hsp.dto.OrderDetailDTO;
import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;

public interface OrderService {

    /**
     * 查询标准订单及其最近支付链路详情。
     */
    OrderDetailDTO detail(String orderNo);

    /**
     * 分页查询订单中心列表。
     */
    PageResultDTO<OrderListItemDTO> list(OrderQueryDTO query);

    /**
     * 导出订单中心列表，供运营、测试和排障留痕使用。
     */
    String exportCsv(OrderQueryDTO query);
}
