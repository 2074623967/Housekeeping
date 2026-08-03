package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestOverviewDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import com.abc123.hsp.service.PaymentRequestService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 支付请求业务实现。
 */
@Service
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private static final Map<String, String> REQUEST_STATUS_ALIASES = buildRequestStatusAliases();
    private static final Pattern IP_ADDRESS_PATTERN = Pattern.compile("^\\d{1,3}(?:\\.\\d{1,3}){3}$");

    private final PaymentRequestMapper paymentRequestMapper;

    public PaymentRequestServiceImpl(PaymentRequestMapper paymentRequestMapper) {
        this.paymentRequestMapper = paymentRequestMapper;
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
        List<PaymentRequestListItemDTO> items = maskRequestItems(paymentRequestMapper.findAllForExport(query));
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
        for (PaymentRequestListItemDTO item : items) {
            if (item == null) {
                continue;
            }
            item.setClientIp(maskClientIp(item.getClientIp()));
            item.setIdempotencyKey(maskIdempotencyKey(item.getIdempotencyKey()));
            item.setRequestPayload(maskPayload(item.getRequestPayload()));
            item.setResponsePayload(maskPayload(item.getResponsePayload()));
        }
        return items;
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
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private String maskClientIp(String clientIp) {
        if (!StringUtils.hasText(clientIp) || "UNKNOWN".equalsIgnoreCase(clientIp.trim())) {
            return clientIp;
        }
        String normalized = clientIp.trim();
        if (IP_ADDRESS_PATTERN.matcher(normalized).matches()) {
            String[] parts = normalized.split("\\.");
            return parts[0] + "." + parts[1] + ".*.*";
        }
        if (normalized.contains(":")) {
            String[] segments = normalized.split(":");
            return segments.length == 0 ? normalized : segments[0] + ":****";
        }
        if (normalized.length() <= 4) {
            return "****";
        }
        return normalized.substring(0, 2) + "****";
    }

    private String maskIdempotencyKey(String idempotencyKey) {
        if (!StringUtils.hasText(idempotencyKey)) {
            return idempotencyKey;
        }
        String normalized = idempotencyKey.trim();
        if (normalized.length() <= 10) {
            return normalized.substring(0, Math.min(2, normalized.length())) + "****";
        }
        return normalized.substring(0, 6) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String maskPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return payload;
        }
        String masked = payload;
        masked = replaceSensitiveField(masked, "clientIp", this::maskClientIp);
        masked = replaceSensitiveField(masked, "idempotencyKey", this::maskIdempotencyKey);
        masked = replaceSensitiveField(masked, "mobile", this::maskPhone);
        masked = replaceSensitiveField(masked, "phone", this::maskPhone);
        masked = replaceSensitiveField(masked, "tel", this::maskPhone);
        masked = replaceSensitiveField(masked, "bankCardNo", this::maskBankCardNo);
        masked = replaceSensitiveField(masked, "cardNo", this::maskBankCardNo);
        masked = replaceSensitiveField(masked, "customerName", this::maskName);
        masked = replaceSensitiveField(masked, "name", this::maskName);
        return masked;
    }

    private String replaceSensitiveField(String payload, String fieldName, Function<String, String> masker) {
        Pattern pattern = Pattern.compile("(\"" + fieldName + "\"\\s*:\\s*\")([^\"]*)(\")");
        Matcher matcher = pattern.matcher(payload);
        StringBuffer stringBuffer = new StringBuffer();
        while (matcher.find()) {
            String replacement = matcher.group(1) + masker.apply(matcher.group(2)) + matcher.group(3);
            matcher.appendReplacement(stringBuffer, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(stringBuffer);
        return stringBuffer.toString();
    }

    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone)) {
            return phone;
        }
        String normalized = phone.trim();
        if (normalized.length() <= 7) {
            return normalized.substring(0, Math.min(3, normalized.length())) + "****";
        }
        return normalized.substring(0, 3) + "****" + normalized.substring(normalized.length() - 4);
    }

    private String maskBankCardNo(String bankCardNo) {
        if (!StringUtils.hasText(bankCardNo)) {
            return bankCardNo;
        }
        String normalized = bankCardNo.trim();
        if (normalized.length() <= 10) {
            return normalized.substring(0, Math.min(4, normalized.length())) + "****";
        }
        return normalized.substring(0, 6) + "******" + normalized.substring(normalized.length() - 4);
    }

    private String maskName(String name) {
        if (!StringUtils.hasText(name)) {
            return name;
        }
        String normalized = name.trim();
        if (normalized.length() <= 1) {
            return "*";
        }
        return normalized.substring(0, 1) + "*";
    }
}
