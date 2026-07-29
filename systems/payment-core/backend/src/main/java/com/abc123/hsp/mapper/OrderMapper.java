package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.OrderListItemDTO;
import com.abc123.hsp.dto.OrderQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 订单中心 Mapper，负责订单运营列表查询。
 */
public interface OrderMapper {

    /**
     * 查询订单中心分页列表。
     */
    List<OrderListItemDTO> findAll(@Param("query") OrderQueryDTO query);

    /**
     * 统计符合条件的订单总数。
     */
    long count(@Param("query") OrderQueryDTO query);
}
