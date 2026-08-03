package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
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
        normalizeQuery(query);
        return new PageResultDTO<>(
                paymentRouteExecutionMapper.findAll(query),
                paymentRouteExecutionMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentRouteExecutionOverviewDTO overview(PaymentRouteExecutionQueryDTO query) {
        normalizeQuery(query);
        PaymentRouteExecutionOverviewDTO overview = paymentRouteExecutionMapper.findOverview(query);
        if (overview == null) {
            overview = new PaymentRouteExecutionOverviewDTO();
        }
        if (overview.getTotalRouteCount() == null) {
            overview.setTotalRouteCount(0L);
        }
        if (overview.getSuccessRouteCount() == null) {
            overview.setSuccessRouteCount(0L);
        }
        if (overview.getWarnRouteCount() == null) {
            overview.setWarnRouteCount(0L);
        }
        if (overview.getDistinctChannelCount() == null) {
            overview.setDistinctChannelCount(0);
        }
        if (overview.getOfflineRouteCount() == null) {
            overview.setOfflineRouteCount(0);
        }
        if (overview.getWechatRouteCount() == null) {
            overview.setWechatRouteCount(0);
        }
        if (overview.getAlipayRouteCount() == null) {
            overview.setAlipayRouteCount(0);
        }
        return overview;
    }

    private void normalizeQuery(PaymentRouteExecutionQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setRouteRule(query.getRouteRule() == null ? null : query.getRouteRule().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setPaymentMethod(query.getPaymentMethod() == null ? "全部" : query.getPaymentMethod().trim());
        query.setTerminal(query.getTerminal() == null ? "全部" : query.getTerminal().trim());
        query.setRouteResult(query.getRouteResult() == null ? "全部" : query.getRouteResult().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }
}
