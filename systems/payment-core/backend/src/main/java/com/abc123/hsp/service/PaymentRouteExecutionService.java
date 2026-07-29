package com.abc123.hsp.service;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;

/**
 * 支付路由执行结果服务。
 */
public interface PaymentRouteExecutionService {

    /**
     * 查询支付路由执行结果列表。
     */
    PageResultDTO<PaymentRouteExecutionListItemDTO> list(PaymentRouteExecutionQueryDTO query);
}
