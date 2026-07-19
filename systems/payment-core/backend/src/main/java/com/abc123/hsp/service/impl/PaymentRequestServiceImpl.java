package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PaymentRequestListItemDTO;
import com.abc123.hsp.dto.PaymentRequestQueryDTO;
import com.abc123.hsp.mapper.PaymentRequestMapper;
import com.abc123.hsp.service.PaymentRequestService;
import java.util.List;
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
    public List<PaymentRequestListItemDTO> list(PaymentRequestQueryDTO query) {
        query.setRequestNo(query.getRequestNo() == null ? null : query.getRequestNo().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        return paymentRequestMapper.findAll(query);
    }
}
