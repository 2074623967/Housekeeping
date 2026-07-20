package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付路由执行结果 Mapper。
 */
public interface PaymentRouteExecutionMapper {

    /**
     * 查询支付路由执行结果列表。
     */
    List<PaymentRouteExecutionListItemDTO> findAll(@Param("query") PaymentRouteExecutionQueryDTO query);

    /**
     * 统计支付路由执行结果总数。
     */
    long count(@Param("query") PaymentRouteExecutionQueryDTO query);
}
