package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.CashierSessionListItemDTO;
import com.abc123.hsp.dto.CashierSessionOverviewDTO;
import com.abc123.hsp.dto.CashierSessionQueryDTO;
import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.mapper.CashierSessionMapper;
import com.abc123.hsp.service.CashierSessionService;
import org.springframework.stereotype.Service;

/**
 * 收银台会话业务实现。
 */
@Service
public class CashierSessionServiceImpl implements CashierSessionService {

    private final CashierSessionMapper cashierSessionMapper;

    public CashierSessionServiceImpl(CashierSessionMapper cashierSessionMapper) {
        this.cashierSessionMapper = cashierSessionMapper;
    }

    @Override
    public PageResultDTO<CashierSessionListItemDTO> list(CashierSessionQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                cashierSessionMapper.findAll(query),
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
        for (CashierSessionListItemDTO item : cashierSessionMapper.findAllForExport(query)) {
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

    private void normalizeQuery(CashierSessionQueryDTO query) {
        query.setSessionNo(query.getSessionNo() == null ? null : query.getSessionNo().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setCustomerName(query.getCustomerName() == null ? null : query.getCustomerName().trim());
        query.setTerminal(query.getTerminal() == null ? "全部" : query.getTerminal().trim());
        query.setSessionStatus(query.getSessionStatus() == null ? "全部" : query.getSessionStatus().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }

    private String csvCell(String value) {
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
