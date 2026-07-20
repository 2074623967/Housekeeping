package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.PaymentEventListItemDTO;
import com.abc123.hsp.dto.PaymentEventQueryDTO;
import com.abc123.hsp.dto.PaymentEventRepublishRequestDTO;
import com.abc123.hsp.mapper.PaymentEventMapper;
import com.abc123.hsp.service.PaymentEventService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 支付事件出站服务实现。
 */
@Service
public class PaymentEventServiceImpl implements PaymentEventService {

    private final PaymentEventMapper paymentEventMapper;

    public PaymentEventServiceImpl(PaymentEventMapper paymentEventMapper) {
        this.paymentEventMapper = paymentEventMapper;
    }

    @Override
    public PageResultDTO<PaymentEventListItemDTO> list(PaymentEventQueryDTO query) {
        normalizeQuery(query);
        return new PageResultDTO<>(
                paymentEventMapper.findAll(query),
                paymentEventMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }

    @Override
    @Transactional
    public PageResultDTO<PaymentEventListItemDTO> republish(
            PaymentEventRepublishRequestDTO request,
            PaymentEventQueryDTO query) {
        if (request == null || !StringUtils.hasText(request.getEventNo())) {
            throw new IllegalArgumentException("事件号不能为空");
        }
        int affectedRows = paymentEventMapper.markRepublished(request.getEventNo().trim());
        if (affectedRows == 0) {
            throw new IllegalArgumentException("支付事件不存在");
        }
        return list(query == null ? new PaymentEventQueryDTO() : query);
    }

    private void normalizeQuery(PaymentEventQueryDTO query) {
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
    }
}
