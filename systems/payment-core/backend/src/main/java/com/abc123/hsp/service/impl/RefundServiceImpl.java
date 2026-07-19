package com.abc123.hsp.service.impl;

import com.abc123.hsp.dto.PageResultDTO;
import com.abc123.hsp.dto.RefundListItemDTO;
import com.abc123.hsp.dto.RefundQueryDTO;
import com.abc123.hsp.mapper.RefundMapper;
import com.abc123.hsp.service.RefundService;
import org.springframework.stereotype.Service;

@Service
public class RefundServiceImpl implements RefundService {

    private final RefundMapper refundMapper;

    public RefundServiceImpl(RefundMapper refundMapper) {
        this.refundMapper = refundMapper;
    }

    @Override
    public PageResultDTO<RefundListItemDTO> list(RefundQueryDTO query) {
        query.setRefundOrderId(query.getRefundOrderId() == null ? null : query.getRefundOrderId().trim());
        query.setPaymentOrderId(query.getPaymentOrderId() == null ? null : query.getPaymentOrderId().trim());
        query.setPageNo(Math.max(query.getPageNo(), 1));
        query.setPageSize(Math.min(Math.max(query.getPageSize(), 1), 100));
        return new PageResultDTO<>(
                refundMapper.findAll(query),
                refundMapper.count(query),
                query.getPageNo(),
                query.getPageSize()
        );
    }
}
