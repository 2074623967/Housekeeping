package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import com.abc123.hsp.service.PaymentRequestService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付请求业务实现。
 */
@Service
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Map<String, String> REQUEST_STATUS_ALIASES = buildRequestStatusAliases();
    private final PaymentRequestMapper paymentRequestMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public PaymentRequestServiceImpl(PaymentRequestMapper paymentRequestMapper,
                                     PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentRequestMapper = paymentRequestMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    PaymentRequestServiceImpl(PaymentRequestMapper paymentRequestMapper) {
        this(paymentRequestMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<PaymentRequestListItemDTO> list(PaymentRequestQueryDTO query) {
        normalizeQuery(query);
        List<PaymentRequestListItemDTO> items = paymentRequestMapper.findAll(query);
        return new PageResultDTO<>(
                maskRequestItems(items),
                paymentRequestMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentRequestOverviewDTO overview(PaymentRequestQueryDTO query) {
        normalizeQuery(query);
        PaymentRequestOverviewDTO overview = paymentRequestMapper.findOverviewSummary(query);
        if (overview == null) {
            overview = new PaymentRequestOverviewDTO();
        }
        if (overview.getTotalRequestCount() == null) {
            overview.setTotalRequestCount(0L);
        }
        if (overview.getSuccessRequestCount() == null) {
            overview.setSuccessRequestCount(0L);
        }
        if (overview.getFailedRequestCount() == null) {
            overview.setFailedRequestCount(0L);
        }
        if (overview.getProcessingRequestCount() == null) {
            overview.setProcessingRequestCount(0L);
        }
        if (overview.getWaitingCallbackRequestCount() == null) {
            overview.setWaitingCallbackRequestCount(0L);
        }
        if (overview.getDistinctTerminalCount() == null) {
            overview.setDistinctTerminalCount(0);
        }
        if (overview.getDistinctChannelCount() == null) {
            overview.setDistinctChannelCount(0);
        }
        if (overview.getRepeatedPaymentOrderCount() == null) {
            overview.setRepeatedPaymentOrderCount(0);
        }
        if (overview.getMissingResponseCount() == null) {
            overview.setMissingResponseCount(0);
        }
        return overview;
    }

    @Override
    public String exportCsv(PaymentRequestQueryDTO query) {
        normalizeQuery(query);
        List<PaymentRequestListItemDTO> items = maskRequestItems(
                CsvExportRowLimiter.requireWithinLimit(paymentRequestMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("请求编号,支付单号,预付单号,订单号,支付方式,渠道编码,路由结果,终端,客户端IP,幂等键,请求状态,创建时间,请求报文,响应报文\n");
        for (PaymentRequestListItemDTO item : items) {
            builder.append(csvCell(item.getRequestNo())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getPrepayOrderNo())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getPaymentMethod())).append(',')
                    .append(csvCell(item.getChannelCode())).append(',')
                    .append(csvCell(item.getRouteResult())).append(',')
                    .append(csvCell(item.getTerminal())).append(',')
                    .append(csvCell(item.getClientIp())).append(',')
                    .append(csvCell(item.getIdempotencyKey())).append(',')
                    .append(csvCell(item.getRequestStatus())).append(',')
                    .append(csvCell(item.getCreatedAt())).append(',')
                    .append(csvCell(item.getRequestPayload())).append(',')
                    .append(csvCell(item.getResponsePayload())).append('\n');
        }
        return builder.toString();
    }

    private List<PaymentRequestListItemDTO> maskRequestItems(List<PaymentRequestListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private PaymentRequestListItemDTO maskedCopy(PaymentRequestListItemDTO item) {
        if (item == null) {
            return null;
        }
        PaymentRequestListItemDTO copy = new PaymentRequestListItemDTO();
        BeanUtils.copyProperties(item, copy);
        copy.setClientIp(sensitiveDataMasker.maskClientIp(item.getClientIp()));
        copy.setIdempotencyKey(sensitiveDataMasker.maskIdempotencyKey(item.getIdempotencyKey()));
        copy.setRequestPayload(sensitiveDataMasker.maskPayload(item.getRequestPayload()));
        copy.setResponsePayload(sensitiveDataMasker.maskPayload(item.getResponsePayload()));
        return copy;
    }

    private void normalizeQuery(PaymentRequestQueryDTO query) {
        query.setRequestNo(query.getRequestNo() == null ? null : query.getRequestNo().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setTerminal(query.getTerminal() == null ? null : query.getTerminal().trim());
        query.setClientIp(query.getClientIp() == null ? null : query.getClientIp().trim());
        query.setRequestStatus(normalizeRequestStatus(query.getRequestStatus()));
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String normalizeRequestStatus(String requestStatus) {
        if (!StringUtils.hasText(requestStatus)) {
            return "全部";
        }
        String normalizedStatus = requestStatus.trim();
        return REQUEST_STATUS_ALIASES.getOrDefault(normalizedStatus, normalizedStatus);
    }

    private static Map<String, String> buildRequestStatusAliases() {
        Map<String, String> aliases = new LinkedHashMap<String, String>();
        aliases.put("请求已发起", "处理中");
        aliases.put("请求成功", "成功");
        aliases.put("请求失败", "失败");
        aliases.put("已关闭", "已关闭");
        aliases.put("处理中", "处理中");
        aliases.put("等待回调", "等待回调");
        aliases.put("成功", "成功");
        aliases.put("失败", "失败");
        aliases.put("全部", "全部");
        return Collections.unmodifiableMap(aliases);
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

}
