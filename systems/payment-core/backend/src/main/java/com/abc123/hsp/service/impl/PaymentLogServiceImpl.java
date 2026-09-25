package com.abc123.hsp.service.impl;

import com.abc123.hsp.common.BusinessException;
import com.abc123.hsp.common.ErrorCode;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentLogDetailDTO;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogOverviewDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import com.abc123.hsp.service.PaymentLogService;
import com.abc123.hsp.service.support.CsvExportSanitizer;
import com.abc123.hsp.service.support.CsvExportRowLimiter;
import java.util.Collections;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 支付处理日志业务实现。
 */
@Service
public class PaymentLogServiceImpl implements PaymentLogService {

    private static final int LOG_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int PROCESS_STAGE_MAX_LENGTH = 32;
    private static final int LOG_LEVEL_MAX_LENGTH = 16;
    private static final int SOURCE_MAX_LENGTH = 64;
    private static final int KEYWORD_MAX_LENGTH = 16383;

    private final PaymentLogMapper paymentLogMapper;

    public PaymentLogServiceImpl(PaymentLogMapper paymentLogMapper) {
        this.paymentLogMapper = paymentLogMapper;
    }

    @Override
    public PaymentLogDetailDTO detail(String logNo) {
        String normalizedLogNo = normalizeRequiredLogNo(logNo);
        PaymentLogDetailDTO detail = paymentLogMapper.findDetail(normalizedLogNo);
        if (detail == null) {
            throw new BusinessException(ErrorCode.PAYMENT_LOG_NOT_FOUND, "支付处理日志不存在");
        }
        return detail;
    }

    @Override
    public PageResultDTO<PaymentLogListItemDTO> list(PaymentLogQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                safeList(paymentLogMapper.findAll(query)),
                paymentLogMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public PaymentLogOverviewDTO overview(PaymentLogQueryDTO query) {
        normalizeQuery(query);
        PaymentLogOverviewDTO overview = paymentLogMapper.findOverviewSummary(query);
        if (overview == null) {
            overview = new PaymentLogOverviewDTO();
        }
        if (overview.getTotalLogCount() == null) {
            overview.setTotalLogCount(0L);
        }
        if (overview.getErrorLogCount() == null) {
            overview.setErrorLogCount(0L);
        }
        if (overview.getWarnLogCount() == null) {
            overview.setWarnLogCount(0L);
        }
        if (overview.getInfoLogCount() == null) {
            overview.setInfoLogCount(0L);
        }
        if (overview.getDistinctStageCount() == null) {
            overview.setDistinctStageCount(0);
        }
        if (overview.getDistinctSourceCount() == null) {
            overview.setDistinctSourceCount(0);
        }
        if (overview.getCallbackErrorCount() == null) {
            overview.setCallbackErrorCount(0);
        }
        if (overview.getEventWarnCount() == null) {
            overview.setEventWarnCount(0);
        }
        if (overview.getCallbackKeywordCount() == null) {
            overview.setCallbackKeywordCount(0);
        }
        return overview;
    }

    @Override
    public String exportCsv(PaymentLogQueryDTO query) {
        normalizeQuery(query);

        java.util.List<PaymentLogListItemDTO> items = CsvExportRowLimiter.requireWithinLimit(
                paymentLogMapper.findAllForExport(query));
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("日志编号,支付单号,订单号,处理阶段,日志级别,来源,日志消息,创建时间\n");
        for (PaymentLogListItemDTO item : items) {
            builder.append(csvCell(item.getLogNo())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getProcessStage())).append(',')
                    .append(csvCell(item.getLogLevel())).append(',')
                    .append(csvCell(item.getSource())).append(',')
                    .append(csvCell(item.getMessage())).append(',')
                    .append(csvCell(item.getCreatedAt())).append('\n');
        }
        return builder.toString();
    }

    private String csvCell(String value) {
        String normalizedValue = CsvExportSanitizer.preventFormula(value).replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }

    private <T> List<T> safeList(List<T> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new java.util.ArrayList<>(values.size());
        for (T value : values) {
            if (value != null) {
                result.add(value);
            }
        }
        return result;
    }

    private void normalizeQuery(PaymentLogQueryDTO query) {
        query.setPaymentOrderId(normalizeOptionalQueryField(
                query.getPaymentOrderId(), PAYMENT_ORDER_ID_MAX_LENGTH, "支付单号", null));
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setProcessStage(normalizeOptionalQueryField(
                query.getProcessStage(), PROCESS_STAGE_MAX_LENGTH, "处理阶段", "全部"));
        query.setLogLevel(normalizeOptionalQueryField(
                query.getLogLevel(), LOG_LEVEL_MAX_LENGTH, "日志级别", "全部"));
        query.setSource(normalizeOptionalQueryField(
                query.getSource(), SOURCE_MAX_LENGTH, "来源", null));
        query.setKeyword(normalizeOptionalQueryField(
                query.getKeyword(), KEYWORD_MAX_LENGTH, "关键字", null));
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String normalizeRequiredLogNo(String logNo) {
        if (logNo == null || logNo.trim().isEmpty()) {
            throw invalidLog("日志编号不能为空");
        }
        rejectControlCharacters(logNo, "日志编号不能包含控制字符");
        String normalized = logNo.trim();
        if (normalized.length() > LOG_NO_MAX_LENGTH) {
            throw invalidLog("日志编号不能超过64个字符");
        }
        return normalized;
    }

    private void rejectControlCharacters(String value, String message) {
        for (int index = 0; index < value.length(); index++) {
            if (Character.isISOControl(value.charAt(index))) {
                throw invalidLog(message);
            }
        }
    }

    private BusinessException invalidLog(String message) {
        return new BusinessException(ErrorCode.PAYMENT_REQUEST_INVALID, message);
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
}
