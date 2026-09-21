package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentRouteExecutionDetailDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付路由执行结果 Mapper。
 */
public interface PaymentRouteExecutionMapper {

    /**
     * 按路由记录号查询支付路由执行详情。
     */
    PaymentRouteExecutionDetailDTO findDetail(@Param("routeNo") String routeNo);

    /**
     * 查询支付路由执行结果列表。
     */
    List<PaymentRouteExecutionListItemDTO> findAll(@Param("query") PaymentRouteExecutionQueryDTO query);

    /**
     * 查询当前筛选条件下的全部支付路由执行结果，用于导出。
     */
    List<PaymentRouteExecutionListItemDTO> findAllForExport(
            @Param("query") PaymentRouteExecutionQueryDTO query);

    /**
     * 查询支付路由执行结果总览。
     */
    PaymentRouteExecutionOverviewDTO findOverview(@Param("query") PaymentRouteExecutionQueryDTO query);

    /**
     * 统计支付路由执行结果总数。
     */
    long count(@Param("query") PaymentRouteExecutionQueryDTO query);
}
