package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.mapper.PaymentRouteExecutionMapper;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 支付路由执行结果服务实现。
 */
@Service
public class PaymentRouteExecutionServiceImpl implements PaymentRouteExecutionService {

    private final PaymentRouteExecutionMapper paymentRouteExecutionMapper;
    private final boolean routeHealthEnabled;
    private final int routeHealthLookbackHours;
    private final int routeHealthFailureCountThreshold;
    private final int routeHealthFailureRateThresholdPercent;
    private final int routeHealthMinimumSampleCount;

    @Autowired
    public PaymentRouteExecutionServiceImpl(
            PaymentRouteExecutionMapper paymentRouteExecutionMapper,
            @Value("${payment.channel-query.monitor.route-health-enabled:false}") boolean routeHealthEnabled,
            @Value("${payment.channel-query.monitor.lookback-hours:24}") int routeHealthLookbackHours,
            @Value("${payment.channel-query.monitor.failure-count-threshold:5}")
                    int routeHealthFailureCountThreshold,
            @Value("${payment.channel-query.monitor.failure-rate-threshold-percent:30}")
                    int routeHealthFailureRateThresholdPercent,
            @Value("${payment.channel-query.monitor.failure-rate-minimum-sample-count:10}")
                    int routeHealthMinimumSampleCount) {
        this.paymentRouteExecutionMapper = paymentRouteExecutionMapper;
        this.routeHealthEnabled = routeHealthEnabled;
        this.routeHealthLookbackHours = Math.max(routeHealthLookbackHours, 1);
        this.routeHealthFailureCountThreshold = Math.max(routeHealthFailureCountThreshold, 1);
        this.routeHealthFailureRateThresholdPercent = Math.min(
                Math.max(routeHealthFailureRateThresholdPercent, 1), 100);
        this.routeHealthMinimumSampleCount = Math.max(routeHealthMinimumSampleCount, 1);
    }

    PaymentRouteExecutionServiceImpl(PaymentRouteExecutionMapper paymentRouteExecutionMapper) {
        this(paymentRouteExecutionMapper, false, 24, 5, 30, 10);
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
        overview.setRouteHealthEnabled(routeHealthEnabled);
        overview.setRouteHealthLookbackHours(routeHealthLookbackHours);
        overview.setRouteHealthFailureCountThreshold(routeHealthFailureCountThreshold);
        overview.setRouteHealthFailureRateThresholdPercent(routeHealthFailureRateThresholdPercent);
        overview.setRouteHealthMinimumSampleCount(routeHealthMinimumSampleCount);
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
