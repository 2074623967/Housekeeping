package com.abc123.hsp.mapper;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import java.util.List;

/**
 * 支付请求 Mapper，负责支付尝试和路由结果查询。
 */
public interface PaymentRequestMapper {

    /**
     * 查询支付请求列表。
     *
     * @return 支付请求列表
     */
    List<PaymentRequestListItemDTO> findAll(PaymentRequestQueryDTO query);

    /**
     * 统计符合条件的支付请求总数。
     */
    long count(PaymentRequestQueryDTO query);
}
