package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentLogListItemDTO;
import com.abc123.hsp.dto.PaymentLogQueryDTO;
import com.abc123.hsp.mapper.PaymentLogMapper;
import com.abc123.hsp.service.PaymentLogService;
import org.springframework.stereotype.Service;

/**
 * 支付处理日志业务实现。
 */
@Service
public class PaymentLogServiceImpl implements PaymentLogService {

    private final PaymentLogMapper paymentLogMapper;

    public PaymentLogServiceImpl(PaymentLogMapper paymentLogMapper) {
        this.paymentLogMapper = paymentLogMapper;
    }

    @Override
    public PageResultDTO<PaymentLogListItemDTO> list(PaymentLogQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setSource(query.getSource() == null ? null : query.getSource().trim());
        query.setKeyword(query.getKeyword() == null ? null : query.getKeyword().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentLogMapper.findAll(query),
                paymentLogMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    public String exportCsv(PaymentLogQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setSource(query.getSource() == null ? null : query.getSource().trim());
        query.setKeyword(query.getKeyword() == null ? null : query.getKeyword().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));

        java.util.List<PaymentLogListItemDTO> items = paymentLogMapper.findAllForExport(query);
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
        String normalizedValue = value == null ? "" : value.replace("\"", "\"\"");
        return "\"" + normalizedValue + "\"";
    }
}
