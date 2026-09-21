package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionDetailDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionListItemDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionOverviewDTO;
import com.abc123.hsp.dto.PaymentRouteExecutionQueryDTO;
import com.abc123.hsp.mapper.PaymentRouteExecutionMapper;
import com.abc123.hsp.service.PaymentRouteExecutionService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 支付路由执行结果服务实现。
 */
@Service
public class PaymentRouteExecutionServiceImpl implements PaymentRouteExecutionService {

    private static final int ROUTE_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int ROUTE_RULE_MAX_LENGTH = 128;
    private static final int CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int PAYMENT_METHOD_MAX_LENGTH = 32;
    private static final int TERMINAL_MAX_LENGTH = 32;
    private static final int ROUTE_RESULT_MAX_LENGTH = 128;

    private final PaymentRouteExecutionMapper paymentRouteExecutionMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;
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
                    int routeHealthMinimumSampleCount,
            PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentRouteExecutionMapper = paymentRouteExecutionMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
        this.routeHealthEnabled = routeHealthEnabled;
        this.routeHealthLookbackHours = Math.max(routeHealthLookbackHours, 1);
        this.routeHealthFailureCountThreshold = Math.max(routeHealthFailureCountThreshold, 1);
        this.routeHealthFailureRateThresholdPercent = Math.min(
                Math.max(routeHealthFailureRateThresholdPercent, 1), 100);
        this.routeHealthMinimumSampleCount = Math.max(routeHealthMinimumSampleCount, 1);
    }

    PaymentRouteExecutionServiceImpl(PaymentRouteExecutionMapper paymentRouteExecutionMapper) {
        this(paymentRouteExecutionMapper, false, 24, 5, 30, 10,
                new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PaymentRouteExecutionDetailDTO detail(String routeNo) {
        String normalizedRouteNo = normalizeRequiredRouteNo(routeNo);
        PaymentRouteExecutionDetailDTO detail = paymentRouteExecutionMapper.findDetail(normalizedRouteNo);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_ROUTE_NOT_FOUND, "支付路由记录不存在");
        }
        return maskedDetail(detail);
    }

    @Override
    public PageResultDTO<PaymentRouteExecutionListItemDTO> list(PaymentRouteExecutionQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(paymentRouteExecutionMapper.findAll(query)),
                paymentRouteExecutionMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(PaymentRouteExecutionQueryDTO query) {
        normalizeQuery(query);
        List<PaymentRouteExecutionListItemDTO> items = maskItems(
                CsvExportRowLimiter.requireWithinLimit(
                        paymentRouteExecutionMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("路由记录号,支付单号,订单号,预付单号,客户名称,支付金额,支付方式,命中渠道,路由规则,路由结果,配置快照版本,终端,客户端IP,幂等键,请求报文,响应报文,创建时间\n");
        for (PaymentRouteExecutionListItemDTO item : items) {
            builder.append(csvCell(item.getRouteNo())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getPrepayOrderNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getAmount())).append(',')
                    .append(csvCell(item.getPaymentMethod())).append(',')
                    .append(csvCell(item.getChannelCode())).append(',')
                    .append(csvCell(item.getRouteRule())).append(',')
                    .append(csvCell(item.getRouteResult())).append(',')
                    .append(csvCell(item.getOpsConfigSnapshotVersion() == null
                            ? null : String.valueOf(item.getOpsConfigSnapshotVersion()))).append(',')
                    .append(csvCell(item.getTerminal())).append(',')
                    .append(csvCell(item.getClientIp())).append(',')
                    .append(csvCell(item.getIdempotencyKey())).append(',')
                    .append(csvCell(item.getRequestPayload())).append(',')
                    .append(csvCell(item.getResponsePayload())).append(',')
                    .append(csvCell(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    private List<PaymentRouteExecutionListItemDTO> maskItems(List<PaymentRouteExecutionListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private PaymentRouteExecutionListItemDTO maskedCopy(PaymentRouteExecutionListItemDTO item) {
        if (item == null) {
            return null;
        }
        PaymentRouteExecutionListItemDTO copy = new PaymentRouteExecutionListItemDTO();
        BeanUtils.copyProperties(item, copy);
        copy.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        copy.setClientIp(sensitiveDataMasker.maskClientIp(item.getClientIp()));
        copy.setIdempotencyKey(sensitiveDataMasker.maskIdempotencyKey(item.getIdempotencyKey()));
        copy.setRequestPayload(sensitiveDataMasker.maskPayload(item.getRequestPayload()));
        copy.setResponsePayload(sensitiveDataMasker.maskPayload(item.getResponsePayload()));
        return copy;
    }

    private PaymentRouteExecutionDetailDTO maskedDetail(PaymentRouteExecutionDetailDTO detail) {
        PaymentRouteExecutionDetailDTO copy = new PaymentRouteExecutionDetailDTO();
        BeanUtils.copyProperties(detail, copy);
        copy.setCustomerName(sensitiveDataMasker.maskName(detail.getCustomerName()));
        copy.setClientIp(sensitiveDataMasker.maskClientIp(detail.getClientIp()));
        copy.setIdempotencyKey(sensitiveDataMasker.maskIdempotencyKey(detail.getIdempotencyKey()));
        copy.setRequestPayload(sensitiveDataMasker.maskPayload(detail.getRequestPayload()));
        copy.setResponsePayload(sensitiveDataMasker.maskPayload(detail.getResponsePayload()));
        return copy;
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
        query.setPaymentOrderId(normalizeOptionalQueryField(
                query.getPaymentOrderId(), PAYMENT_ORDER_ID_MAX_LENGTH, "支付单号", null));
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setRouteRule(normalizeOptionalQueryField(
                query.getRouteRule(), ROUTE_RULE_MAX_LENGTH, "路由规则", null));
        query.setChannelCode(normalizeOptionalQueryField(
                query.getChannelCode(), CHANNEL_CODE_MAX_LENGTH, "渠道编码", null));
        query.setPaymentMethod(normalizeOptionalQueryField(
                query.getPaymentMethod(), PAYMENT_METHOD_MAX_LENGTH, "支付方式", "全部"));
        query.setTerminal(normalizeOptionalQueryField(
                query.getTerminal(), TERMINAL_MAX_LENGTH, "终端", "全部"));
        query.setRouteResult(normalizeOptionalQueryField(
                query.getRouteResult(), ROUTE_RESULT_MAX_LENGTH, "路由结果", "全部"));
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String normalizeOptionalQueryField(
            String value, int maxLength, String fieldName, String defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw new IllegalArgumentException(fieldName + "不能包含控制字符");
            }
        }
        String normalized = value.trim();
        if (normalized.length() > maxLength) {
            throw new IllegalArgumentException(fieldName + "不能超过" + maxLength + "个字符");
        }
        return normalized;
    }

    private String normalizeRequiredRouteNo(String routeNo) {
        if (routeNo == null || routeNo.trim().isEmpty()) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, "路由记录号不能为空");
        }
        for (int index = 0; index < routeNo.length(); index++) {
            if (Character.isISOControl(routeNo.charAt(index))) {
                throw new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, "路由记录号不能包含控制字符");
            }
        }
        String normalized = routeNo.trim();
        if (normalized.length() > ROUTE_NO_MAX_LENGTH) {
            throw new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, "路由记录号不能超过64个字符");
        }
        return normalized;
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
