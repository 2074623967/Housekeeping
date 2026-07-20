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
}
