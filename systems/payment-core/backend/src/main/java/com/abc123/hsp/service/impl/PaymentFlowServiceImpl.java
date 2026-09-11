package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import com.abc123.hsp.service.PaymentFlowService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import com.abc123.hsp.service.support.PaymentSensitiveDataMasker;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * 支付流水中心业务实现，仅承接支付过程追踪，不承接账务会计处理。
 */
@Service
public class PaymentFlowServiceImpl implements PaymentFlowService {

    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int FLOW_TYPE_MAX_LENGTH = 32;
    private static final int CHANNEL_CODE_MAX_LENGTH = 64;
    private static final int TERMINAL_MAX_LENGTH = 32;
    private static final int BUSINESS_STATUS_MAX_LENGTH = 128;
    private static final int KEYWORD_MAX_LENGTH = 2048;

    private final PaymentFlowMapper paymentFlowMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public PaymentFlowServiceImpl(PaymentFlowMapper paymentFlowMapper,
                                  PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.paymentFlowMapper = paymentFlowMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    PaymentFlowServiceImpl(PaymentFlowMapper paymentFlowMapper) {
        this(paymentFlowMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<PaymentFlowListItemDTO> list(PaymentFlowQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(paymentFlowMapper.findAll(query)),
                paymentFlowMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(PaymentFlowQueryDTO query) {
        normalizeQuery(query);
        List<PaymentFlowListItemDTO> items = maskItems(
                CsvExportRowLimiter.requireWithinLimit(paymentFlowMapper.findAllForExport(query)));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("流水编号,支付单号,订单号,预付单号,流水类型,类型标签,渠道编码,终端,客户端IP,幂等键,业务状态,状态类型,通知类型,路由规则,下游系统,事件主题,发布状态,重试次数,请求报文,响应报文,摘要,创建时间\n");
        for (PaymentFlowListItemDTO item : items) {
            builder.append(csvCell(item.getFlowNo())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getPrepayOrderNo())).append(',')
                    .append(csvCell(item.getFlowType())).append(',')
                    .append(csvCell(item.getFlowTypeTag())).append(',')
                    .append(csvCell(item.getChannelCode())).append(',')
                    .append(csvCell(item.getTerminal())).append(',')
                    .append(csvCell(item.getClientIp())).append(',')
                    .append(csvCell(item.getIdempotencyKey())).append(',')
                    .append(csvCell(item.getBusinessStatus())).append(',')
                    .append(csvCell(item.getBusinessStatusType())).append(',')
                    .append(csvCell(item.getNotifyType())).append(',')
                    .append(csvCell(item.getRouteRule())).append(',')
                    .append(csvCell(item.getDownstreamSystem())).append(',')
                    .append(csvCell(item.getEventTopic())).append(',')
                    .append(csvCell(item.getPublishStatus())).append(',')
                    .append(csvCell(item.getRetryCount() == null ? null : String.valueOf(item.getRetryCount()))).append(',')
                    .append(csvCell(item.getRequestPayload())).append(',')
                    .append(csvCell(item.getResponsePayload())).append(',')
                    .append(csvCell(item.getSummary())).append(',')
                    .append(csvCell(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    private List<PaymentFlowListItemDTO> maskItems(List<PaymentFlowListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private PaymentFlowListItemDTO maskedCopy(PaymentFlowListItemDTO item) {
        if (item == null) {
            return null;
        }
        PaymentFlowListItemDTO copy = new PaymentFlowListItemDTO();
        BeanUtils.copyProperties(item, copy);
        copy.setClientIp(sensitiveDataMasker.maskClientIp(item.getClientIp()));
        copy.setIdempotencyKey(sensitiveDataMasker.maskIdempotencyKey(item.getIdempotencyKey()));
        copy.setRequestPayload(sensitiveDataMasker.maskPayload(item.getRequestPayload()));
        copy.setResponsePayload(sensitiveDataMasker.maskPayload(item.getResponsePayload()));
        return copy;
    }

    private void normalizeQuery(PaymentFlowQueryDTO query) {
        query.setPaymentOrderId(normalizeOptionalQueryField(
                query.getPaymentOrderId(), PAYMENT_ORDER_ID_MAX_LENGTH, "支付单号", null));
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setFlowType(normalizeOptionalQueryField(
                query.getFlowType(), FLOW_TYPE_MAX_LENGTH, "流水类型", "全部"));
        query.setChannelCode(normalizeOptionalQueryField(
                query.getChannelCode(), CHANNEL_CODE_MAX_LENGTH, "渠道编码", null));
        query.setTerminal(normalizeOptionalQueryField(
                query.getTerminal(), TERMINAL_MAX_LENGTH, "终端", "全部"));
        query.setBusinessStatus(normalizeOptionalQueryField(
                query.getBusinessStatus(), BUSINESS_STATUS_MAX_LENGTH, "业务状态", null));
        query.setKeyword(normalizeOptionalQueryField(
                query.getKeyword(), KEYWORD_MAX_LENGTH, "关键字", null));
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

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
