package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.OrderMapper;
import com.abc123.hsp.service.OrderService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

@Service
public class OrderServiceImpl implements OrderService {

    private final OrderMapper orderMapper;

    public OrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public PageResultDTO<OrderListItemDTO> list(OrderQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                orderMapper.findAll(query),
                orderMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    /**
     * 后台运营列表统一在服务层收敛分页边界，避免前端传异常分页导致慢查询。
     */
    private void normalizeQuery(OrderQueryDTO query) {
        query.setOrderNo(StringUtils.hasText(query.getOrderNo()) ? query.getOrderNo().trim() : null);
        query.setServiceType(StringUtils.hasText(query.getServiceType()) ? query.getServiceType().trim() : "全部");
        query.setOrderStatus(StringUtils.hasText(query.getOrderStatus()) ? query.getOrderStatus().trim() : "全部");
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }
}
