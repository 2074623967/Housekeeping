package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionOverviewDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.CashierSessionMapper;
import com.abc123.hsp.service.CashierSessionService;
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
 * 收银台会话业务实现。
 */
@Service
public class CashierSessionServiceImpl implements CashierSessionService {

    private static final int SESSION_NO_MAX_LENGTH = 64;
    private static final int PAYMENT_ORDER_ID_MAX_LENGTH = 64;
    private static final int ORDER_NO_MAX_LENGTH = 64;
    private static final int CUSTOMER_NAME_MAX_LENGTH = 128;
    private static final int TERMINAL_MAX_LENGTH = 32;
    private static final int SESSION_STATUS_MAX_LENGTH = 32;

    private final CashierSessionMapper cashierSessionMapper;
    private final PaymentSensitiveDataMasker sensitiveDataMasker;

    @Autowired
    public CashierSessionServiceImpl(CashierSessionMapper cashierSessionMapper,
                                     PaymentSensitiveDataMasker sensitiveDataMasker) {
        this.cashierSessionMapper = cashierSessionMapper;
        this.sensitiveDataMasker = sensitiveDataMasker;
    }

    CashierSessionServiceImpl(CashierSessionMapper cashierSessionMapper) {
        this(cashierSessionMapper, new PaymentSensitiveDataMasker(new ObjectMapper()));
    }

    @Override
    public PageResultDTO<CashierSessionListItemDTO> list(CashierSessionQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                maskItems(cashierSessionMapper.findAll(query)),
                cashierSessionMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public CashierSessionOverviewDTO overview(CashierSessionQueryDTO query) {
        normalizeQuery(query);
        CashierSessionOverviewDTO overview = cashierSessionMapper.findOverview(query);
        if (overview == null) {
            overview = new CashierSessionOverviewDTO();
        }
        if (overview.getTotalSessionCount() == null) {
            overview.setTotalSessionCount(0L);
        }
        if (overview.getExpiredSessionCount() == null) {
            overview.setExpiredSessionCount(0L);
        }
        if (overview.getSuccessSessionCount() == null) {
            overview.setSuccessSessionCount(0L);
        }
        if (overview.getPayingSessionCount() == null) {
            overview.setPayingSessionCount(0L);
        }
        if (overview.getPendingSessionCount() == null) {
            overview.setPendingSessionCount(0L);
        }
        if (overview.getDistinctTerminalCount() == null) {
            overview.setDistinctTerminalCount(0);
        }
        if (overview.getExpiringSoonCount() == null) {
            overview.setExpiringSoonCount(0);
        }
        if (overview.getTotalAmount() == null) {
            overview.setTotalAmount("¥0.00");
        }
        return overview;
    }

    @Override
    public String exportCsv(CashierSessionQueryDTO query) {
        normalizeQuery(query);
        StringBuilder builder = new StringBuilder("\uFEFF");
        builder.append("会话编号,预付单号,支付单号,订单号,账单号,客户名称,收银台标题,终端,金额,会话状态,状态类型,创建时间,失效时间\n");
        for (CashierSessionListItemDTO item : maskItems(
                CsvExportRowLimiter.requireWithinLimit(cashierSessionMapper.findAllForExport(query)))) {
            builder.append(csvCell(item.getSessionNo())).append(',')
                    .append(csvCell(item.getPrepayOrderNo())).append(',')
                    .append(csvCell(item.getPaymentOrderId())).append(',')
                    .append(csvCell(item.getOrderNo())).append(',')
                    .append(csvCell(item.getBillNo())).append(',')
                    .append(csvCell(item.getCustomerName())).append(',')
                    .append(csvCell(item.getCashierTitle())).append(',')
                    .append(csvCell(item.getTerminal())).append(',')
                    .append(csvCell(item.getAmount())).append(',')
                    .append(csvCell(item.getSessionStatus())).append(',')
                    .append(csvCell(item.getSessionStatusType())).append(',')
                    .append(csvCell(item.getCreatedAt())).append(',')
                    .append(csvCell(item.getExpiresAt())).append('\n');
        }
        return builder.toString();
    }

    private List<CashierSessionListItemDTO> maskItems(List<CashierSessionListItemDTO> items) {
        if (items == null || items.isEmpty()) {
            return Collections.emptyList();
        }
        return items.stream().map(this::maskedCopy).collect(Collectors.toList());
    }

    private CashierSessionListItemDTO maskedCopy(CashierSessionListItemDTO item) {
        if (item == null) {
            return null;
        }
        CashierSessionListItemDTO outwardItem = new CashierSessionListItemDTO();
        BeanUtils.copyProperties(item, outwardItem);
        outwardItem.setCustomerName(sensitiveDataMasker.maskName(item.getCustomerName()));
        return outwardItem;
    }

    private void normalizeQuery(CashierSessionQueryDTO query) {
        query.setSessionNo(normalizeOptionalQueryField(
                query.getSessionNo(), SESSION_NO_MAX_LENGTH, "会话号", null));
        query.setPaymentOrderId(normalizeOptionalQueryField(
                query.getPaymentOrderId(), PAYMENT_ORDER_ID_MAX_LENGTH, "支付单号", null));
        query.setOrderNo(normalizeOptionalQueryField(
                query.getOrderNo(), ORDER_NO_MAX_LENGTH, "订单号", null));
        query.setCustomerName(normalizeOptionalQueryField(
                query.getCustomerName(), CUSTOMER_NAME_MAX_LENGTH, "客户名称", null));
        query.setTerminal(normalizeOptionalQueryField(
                query.getTerminal(), TERMINAL_MAX_LENGTH, "终端", "全部"));
        query.setSessionStatus(normalizeOptionalQueryField(
                query.getSessionStatus(), SESSION_STATUS_MAX_LENGTH, "会话状态", "全部"));
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
