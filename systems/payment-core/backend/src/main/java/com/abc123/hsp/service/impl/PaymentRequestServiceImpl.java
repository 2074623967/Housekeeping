package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import com.abc123.hsp.service.PaymentRequestService;
import org.springframework.stereotype.Service;

/**
 * 支付请求业务实现。
 */
@Service
public class PaymentRequestServiceImpl implements PaymentRequestService {

    private final PaymentRequestMapper paymentRequestMapper;

    public PaymentRequestServiceImpl(PaymentRequestMapper paymentRequestMapper) {
        this.paymentRequestMapper = paymentRequestMapper;
    }

    @Override
    public PageResultDTO<PaymentRequestListItemDTO> list(PaymentRequestQueryDTO query) {
        query.setRequestNo(query.getRequestNo() == null ? null : query.getRequestNo().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setOrderNo(query.getOrderNo() == null ? null : query.getOrderNo().trim());
        query.setChannelCode(query.getChannelCode() == null ? null : query.getChannelCode().trim());
        query.setTerminal(query.getTerminal() == null ? null : query.getTerminal().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                paymentRequestMapper.findAll(query),
                paymentRequestMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
