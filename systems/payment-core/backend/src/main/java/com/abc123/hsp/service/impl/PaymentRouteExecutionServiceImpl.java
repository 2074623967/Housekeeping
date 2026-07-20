package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.mapper.PaymentRouteExecutionMapper;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import org.springframework.stereotype.Service;

/**
 * 支付路由执行结果服务实现。
 */
@Service
public class PaymentRouteExecutionServiceImpl implements PaymentRouteExecutionService {

    private final PaymentRouteExecutionMapper paymentRouteExecutionMapper;

    public PaymentRouteExecutionServiceImpl(PaymentRouteExecutionMapper paymentRouteExecutionMapper) {
        this.paymentRouteExecutionMapper = paymentRouteExecutionMapper;
    }

    @Override
    public PageResultDTO<PaymentRouteExecutionListItemDTO> list(PaymentRouteExecutionQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setRouteRule(query.getRouteRule() == null ? null : query.getRouteRule().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setRouteResult(query.getRouteResult() == null ? "全部" : query.getRouteResult().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentRouteExecutionMapper.findAll(query),
                paymentRouteExecutionMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
