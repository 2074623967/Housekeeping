package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentFlowListItemDTO;
import com.abc123.hsp.dto.PaymentFlowQueryDTO;
import com.abc123.hsp.mapper.PaymentFlowMapper;
import com.abc123.hsp.service.PaymentFlowService;
import org.springframework.stereotype.Service;

/**
 * 支付流水中心业务实现，仅承接支付过程追踪，不承接账务会计处理。
 */
@Service
public class PaymentFlowServiceImpl implements PaymentFlowService {

    private final PaymentFlowMapper paymentFlowMapper;

    public PaymentFlowServiceImpl(PaymentFlowMapper paymentFlowMapper) {
        this.paymentFlowMapper = paymentFlowMapper;
    }

    @Override
    public PageResultDTO<PaymentFlowListItemDTO> list(PaymentFlowQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setTerminal(query.getTerminal() == null ? "全部" : query.getTerminal().trim());
        query.setBusinessStatus(query.getBusinessStatus() == null ? null : query.getBusinessStatus().trim());
        query.setKeyword(query.getKeyword() == null ? null : query.getKeyword().trim());
        query.setSortField(query.getSortField() == null ? "createdAt" : query.getSortField().trim());
        query.setSortOrder(query.getSortOrder() == null ? "desc" : query.getSortOrder().trim().toLowerCase());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentFlowMapper.findAll(query),
                paymentFlowMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
