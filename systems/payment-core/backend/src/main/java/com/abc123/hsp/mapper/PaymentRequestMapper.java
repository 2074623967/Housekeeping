package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 支付请求 Mapper，负责支付尝试和路由结果查询。
 */
public interface PaymentRequestMapper {

    /**
     * 查询支付请求列表。
     *
     * @return 支付请求列表
     */
    List<PaymentRequestListItemDTO> findAll(@Param("query") PaymentRequestQueryDTO query);

    /**
     * 查询导出使用的支付请求列表，不分页。
     *
     * @return 支付请求列表
     */
    List<PaymentRequestListItemDTO> findAllForExport(@Param("query") PaymentRequestQueryDTO query);

    /**
     * 查询支付请求总览指标。
     */
    PaymentRequestOverviewDTO findOverviewSummary(@Param("query") PaymentRequestQueryDTO query);

    /**
     * 统计符合条件的支付请求总数。
     */
    long count(@Param("query") PaymentRequestQueryDTO query);
}
